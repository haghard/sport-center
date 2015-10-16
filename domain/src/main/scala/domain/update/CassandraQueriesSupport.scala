package domain.update

import _root_.dsl.cassandra._
import akka.actor.{ ActorLogging, ActorRef, Actor }
import akka.persistence.PersistentRepr
import akka.serialization.Serialization
import akka.stream._
import akka.stream.scaladsl._
import com.datastax.driver.core.utils.Bytes
import com.datastax.driver.core.{ Cluster, ConsistencyLevel, Row }
import domain.CrawlerCampaign.CampaignPersistedEvent
import domain.TeamAggregate.{ ResultAdded, CreateResult }
import domain.update.DistributedDomainWriter.GetLastChangeSetNumber
import domain.update.WriterGuardian.PersistDataChange
import join.Join
import join.cassandra.CassandraSource
import microservice.crawler.NbaResult
import microservice.settings.CustomSettings

import scala.collection.immutable.SortedSet
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{ Duration, FiniteDuration }
import scala.collection.JavaConverters._
import akka.pattern.ask

trait CassandraQueriesSupport {
  mixin: Actor with ActorLogging {
    def settings: CustomSettings
    def serialization: Serialization
  } =>

  val teamsTable = "teams"
  val campaignTable = "campaign"

  implicit object Sort extends Ordering[CreateResult] {
    override def compare(x: CreateResult, y: CreateResult) =
      x.result.dt.compareTo(y.result.dt)
  }

  implicit val ex = context.system.dispatchers.lookup("stream-dispatcher")

  case class Tick()

  private def readEvery[T](interval: FiniteDuration)(implicit ex: ExecutionContext) = {
    Flow() { implicit b =>
      import FlowGraph.Implicits._
      val zip = b.add(ZipWith[T, Tick.type, T](Keep.left).withAttributes(Attributes.inputBuffer(1, 1)))
      Source(Duration.Zero, interval, Tick) ~> zip.in1
      (zip.in0, zip.out)
    }
  }

  val qCampaign = for { q ← select("SELECT campaign_id FROM {0}") } yield q
  val qTeams = for { q ← select("SELECT processor_id FROM {0}") } yield q

  private def qChanges(seqNum: Long)(r: Row) = for {
    _ ← select(s"select persistence_id, sequence_nr, message from {0} where persistence_id = ? and sequence_nr > $seqNum and partition_nr = 0")
    _ ← fk[java.lang.String]("persistence_id", r.getString("campaign_id"))
    q ← readConsistency(ConsistencyLevel.QUORUM)
  } yield q

  private def qResults(seqNum: Long)(r: Row) = for {
    _ ← select(s"select persistence_id, sequence_nr, message from {0} where persistence_id = ? and sequence_nr > $seqNum and partition_nr = 0")
    _ ← fk[java.lang.String]("persistence_id", r.getString("processor_id"))
    q ← readConsistency(ConsistencyLevel.QUORUM)
  } yield q

  def newClient = Cluster.builder().addContactPointsWithPorts(List(settings.cassandra.address).asJava).build

  private def deserializer0: (CassandraSource#Record, CassandraSource#Record) ⇒ (ddd.EventMessage, Long) =
    (outer, inner) ⇒ {
      val rep = serialization.deserialize(Bytes.getArray(inner.getBytes("message")), classOf[PersistentRepr]).get
      val domainEvent = rep.payload.asInstanceOf[ddd.EventMessage]
      (domainEvent, rep.sequenceNr)
    }

  private def deserializer1: (CassandraSource#Record, CassandraSource#Record) ⇒ NbaResult =
    (outer, inner) ⇒ {
      val rep = serialization.deserialize(Bytes.getArray(inner.getBytes("message")), classOf[PersistentRepr]).get
      val domainEvent = rep.payload.asInstanceOf[ResultAdded]
      domainEvent.r
    }

  private def fetchResult(seqNum: Long)(implicit client: CassandraSource#Client) =
    (Join[CassandraSource] left (qTeams, teamsTable, qResults(seqNum), settings.cassandra.table, settings.cassandra.keyspace))(deserializer1)
      .source

  private def fetchChanges(seqNum: Long)(implicit client: CassandraSource#Client) =
    (Join[CassandraSource] left (qCampaign, campaignTable, qChanges(seqNum), settings.cassandra.table, settings.cassandra.keyspace))(deserializer0)
      .source
      .filter(_._1.event.isInstanceOf[CampaignPersistedEvent])
      .map { kv =>
        val c = kv._1.event.asInstanceOf[CampaignPersistedEvent]
        PersistDataChange(kv._2, (c.results./:(Map[String, SortedSet[CreateResult]]()) { (map, res) ⇒
          val set = map.getOrElse(res.homeTeam, SortedSet[CreateResult]())
          val updated = set + CreateResult(res.homeTeam, res)
          map + (res.homeTeam -> updated)
        }))
      }

  /**
   *
   *
   */
  def changesStream(seqNum: Long, interval: FiniteDuration, client: CassandraSource#Client, des: ActorRef)(implicit Mat: ActorMaterializer): Unit =
    ((fetchChanges(seqNum)(client)) via readEvery(interval))
      .mapAsync(1) { ch => (des.ask(ch)(interval)).mapTo[Long] } //sort of back pressure
      .to(Sink.onComplete { _ =>
        (des.ask(GetLastChangeSetNumber)(interval)).mapTo[Long].map { n =>
          context.system.scheduler.scheduleOnce(interval, new Runnable {
            override def run() = changesStream(n, interval, client, des)
          })
        }
      }).run()(Mat)

  /**
   *
   *
   */
  def resultsStream(seqNum: Long, interval: FiniteDuration, client: CassandraSource#Client, des: ActorRef, acc: Long)(implicit Mat: ActorMaterializer): Unit = {
    (if (acc == 0) fetchResult(seqNum)(client) else fetchResult(seqNum)(client) via readEvery(interval))
      .map { res => des ! res }
      //.mapAsync(1) { res => (des.ask(res.size.toLong)(interval)).mapTo[Long] } //sort of back pressure
      .to(Sink.onComplete { _ =>
        (des.ask(seqNum)(interval)).mapTo[Long].map { n =>
          context.system.scheduler.scheduleOnce(interval, new Runnable {
            override def run() = resultsStream(n, interval, client, des, acc + 1l)
          })
        }
      }).run()(Mat)
  }

  /**
   *
   *
   */
  def viewStream(seqNum: Long, interval: FiniteDuration, client: CassandraSource#Client, des: ActorRef, acc: Long, f: NbaResult => Option[ActorRef])(implicit Mat: ActorMaterializer): Unit = {
    (if (acc == 0) fetchResult(seqNum)(client) else fetchResult(seqNum)(client) via readEvery(interval))
      .grouped(Mat.settings.maxInputBufferSize)
      .map { batch =>
        batch.foreach(r => f(r).fold(log.debug("MaterializedView wasn't found for {}", r.dt))(_ ! r))
      }
      .to(Sink.onComplete { _ =>
        (des.ask(seqNum)(interval)).mapTo[Long].map { n =>
          context.system.scheduler.scheduleOnce(interval, new Runnable {
            override def run() = viewStream(n, interval, client, des, acc + 1l, f)
          })
        }
      }).run()(Mat)
  }
}
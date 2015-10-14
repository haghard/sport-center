package domain.update

import _root_.dsl.cassandra._
import akka.actor.{ ActorLogging, ActorRef, Actor }
import akka.persistence.PersistentRepr
import akka.serialization.Serialization
import akka.stream.{ Supervision, ActorMaterializerSettings, ActorMaterializer, Attributes }
import akka.stream.scaladsl._
import com.datastax.driver.core.utils.Bytes
import com.datastax.driver.core.{ Cluster, ConsistencyLevel, Row }
import domain.CrawlerCampaign.CampaignPersistedEvent
import domain.TeamAggregate.CreateResult
import domain.update.DistributedDomainWriter.GetLastChangeSetNumber
import domain.update.WriterGuardian.PersistDataChange
import join.Join
import join.cassandra.CassandraSource
import microservice.settings.CustomSettings

import scala.collection.immutable.SortedSet
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{ Duration, FiniteDuration }
import scala.collection.JavaConverters._

trait ChangesJournalSupport {
  mixin: Actor with ActorLogging {
    def settings: CustomSettings
    def writeProcessor: ActorRef
    def writeInterval: FiniteDuration
    def serialization: Serialization
  } =>

  implicit object Sort extends Ordering[CreateResult] {
    override def compare(x: CreateResult, y: CreateResult) =
      x.result.dt.compareTo(y.result.dt)
  }

  val decider: Supervision.Decider = {
    case ex ⇒
      log.error(ex, "Capture fetch error")
      Supervision.restart
  }

  implicit val d = context.system.dispatchers.lookup("scheduler-dispatcher")
  implicit val Mat = ActorMaterializer(ActorMaterializerSettings(context.system)
    .withDispatcher("stream-dispatcher")
    .withSupervisionStrategy(decider)
    .withInputBuffer(1, 1))(context.system)

  case class Tick()

  private def readEvery(interval: FiniteDuration)(implicit ex: ExecutionContext) = {
    Flow() { implicit b =>
      import FlowGraph.Implicits._
      val zip = b.add(ZipWith[PersistDataChange, Tick.type, PersistDataChange](Keep.left).withAttributes(Attributes.inputBuffer(1, 1)))
      Source(Duration.Zero, interval, Tick) ~> zip.in1
      (zip.in0, zip.out)
    }
  }

  val qCampaign = for { q ← select("SELECT campaign_id FROM {0}") } yield q

  private def qChanges(seqNum: Long)(r: Row) = for {
    _ ← select(s"select persistence_id, sequence_nr, message from {0} where persistence_id = ? and sequence_nr > $seqNum and partition_nr = 0")
    _ ← fk[java.lang.String]("persistence_id", r.getString("campaign_id"))
    q ← readConsistency(ConsistencyLevel.QUORUM)
  } yield q

  def newClient = Cluster.builder().addContactPointsWithPorts(List(settings.cassandra.address).asJava).build

  private def cmb: (CassandraSource#Record, CassandraSource#Record) ⇒ (ddd.EventMessage, Long) =
    (outer, inner) ⇒ {
      val body = inner.getBytes("message")
      val rep = serialization.deserialize(Bytes.getArray(body), classOf[PersistentRepr]).get
      val domainEvent = rep.payload.asInstanceOf[ddd.EventMessage]
      (domainEvent, rep.sequenceNr)
    }

  private def streamFrom(seqNum: Long)(implicit c: CassandraSource#Client) =
    (Join[CassandraSource] left (qCampaign, "campaign", qChanges(seqNum), settings.cassandra.table, settings.cassandra.keyspace))(cmb)
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

  import akka.pattern.ask
  def process(seqNum: Long, client: CassandraSource#Client): Unit =
    ((streamFrom(seqNum)(newClient)) via readEvery(writeInterval))
      .mapAsync(1) { ch => (writeProcessor.ask(ch)(writeInterval)).mapTo[Long] }
      .to(Sink.onComplete { _ =>
        client.close()
        (writeProcessor.ask(GetLastChangeSetNumber)(writeInterval)).mapTo[Long].map { n =>
          context.system.scheduler.scheduleOnce(writeInterval, new Runnable {
            override def run() = process(n, newClient)
          })
        }
      }).run()(Mat)
}

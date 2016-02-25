package domain.update

import akka.actor.{ ActorRef, Actor }
import akka.persistence.PersistentRepr
import akka.serialization.Serialization
import akka.stream.{ FlowShape, Attributes, ActorMaterializer }
import akka.stream.scaladsl._
import com.datastax.driver.core.{ ConsistencyLevel, Row }
import com.datastax.driver.core.utils.Bytes
import domain.CrawlerCampaign.CampaignPersistedEvent
import domain.TeamAggregate.CreateResult
import domain.update.DomainWriter.GetLastChangeSetOffset
import domain.update.DomainWriterSupervisor.PersistDataChange
import dsl.cassandra._
import join.Join
import join.cassandra.CassandraSource
import microservice.settings.CustomSettings

import scala.collection.immutable.SortedSet
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{ Duration, FiniteDuration }
import akka.pattern.ask

object ChangesStream {
  implicit object Sort extends Ordering[CreateResult] {
    override def compare(x: CreateResult, y: CreateResult) =
      x.result.dt.compareTo(y.result.dt)
  }

  case class Tick()

  def readEvery[T](interval: FiniteDuration)(implicit ex: ExecutionContext) =
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val zip = builder.add(ZipWith[T, Tick, T](Keep.left).withAttributes(Attributes.inputBuffer(1, 1)))
      Source.tick(Duration.Zero, interval, Tick()) ~> zip.in1
      FlowShape(zip.in0, zip.out)
    }

  private val campaignTable = "campaign"
}

trait ChangesStream {
  mixin: CassandraQueriesSupport with Actor {
    def settings: CustomSettings
    def serialization: Serialization
  } =>
  import ChangesStream._

  import scala.concurrent.duration._

  //since akka-cassandra-persistence 0.11
  private def deserializeEvent(serialization: Serialization, row: Row): Any = {
    serialization.deserialize(
      row.getBytes("event").array,
      row.getInt("ser_id"),
      row.getString("ser_manifest")
    ).get
  }

  val deserializer: (CassandraSource#Record, CassandraSource#Record) ⇒ (Any, Long) =
    (outer, inner) ⇒ {
      val rep = deserializeEvent(serialization, inner)
      val seqN = inner.getLong("sequence_nr")
      (rep, seqN)
    }

  val qCampaign = for { q ← select("SELECT campaign_id FROM {0}") } yield q

  def qChanges(seqNum: Long)(r: Row) = for {
    _ ← select(s"select persistence_id, sequence_nr, message from {0} where persistence_id = ? and sequence_nr > $seqNum and partition_nr = 0")
    q ← fk[java.lang.String]("persistence_id", r.getString("campaign_id"))
  } yield q

  private def fetchChanges(seqNum: Long)(implicit client: CassandraSource#Client) =
    (Join[CassandraSource] inner (qCampaign, campaignTable, qChanges(seqNum), settings.cassandra.table, settings.cassandra.keyspace))(deserializer)
      .source
      .filter(_._1.isInstanceOf[CampaignPersistedEvent])
      .map { kv =>
        val c = kv._1.asInstanceOf[CampaignPersistedEvent]
        PersistDataChange(kv._2, (c.results./:(Map[String, SortedSet[CreateResult]]()) { (map, res) ⇒
          val set = map.getOrElse(res.homeTeam, SortedSet[CreateResult]())
          val updated = set + CreateResult(res.homeTeam, res)
          map + (res.homeTeam -> updated)
        }))
      }

  def quorumClient = cassandraClient(ConsistencyLevel.QUORUM)

  def changesStream(seqNum: Long, interval: FiniteDuration, client: CassandraSource#Client, des: ActorRef)(implicit Mat: ActorMaterializer): Unit = {
    ((fetchChanges(seqNum)(client)) via readEvery(interval))
      .mapAsync(1) { ch => (des.ask(ch)(interval)).mapTo[Long] } //sort of back pressure
      .to(Sink.onComplete { _ =>
        (des.ask(GetLastChangeSetOffset)(interval)).mapTo[Long].map { n =>
          context.system.scheduler.scheduleOnce(interval, new Runnable {
            override def run = changesStream(n, interval, client, des)
          })
        }
      }).run()(Mat)
  }
}

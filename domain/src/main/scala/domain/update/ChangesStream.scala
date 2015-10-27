package domain.update

import akka.actor.{ ActorRef, Actor }
import akka.persistence.PersistentRepr
import akka.serialization.Serialization
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import com.datastax.driver.core.{ ConsistencyLevel, Row }
import com.datastax.driver.core.utils.Bytes
import domain.CrawlerCampaign.CampaignPersistedEvent
import domain.TeamAggregate.CreateResult
import domain.update.DistributedDomainWriter.GetLastChangeSetOffset
import domain.update.WriterGuardian.PersistDataChange
import dsl.cassandra._
import join.Join
import join.cassandra.CassandraSource
import microservice.settings.CustomSettings

import scala.collection.immutable.SortedSet
import scala.concurrent.duration.FiniteDuration
import akka.pattern.ask

object ChangesStream {
  implicit object Sort extends Ordering[CreateResult] {
    override def compare(x: CreateResult, y: CreateResult) =
      x.result.dt.compareTo(y.result.dt)
  }

  private val campaignTable = "campaign"
}

trait ChangesStream {
  mixin: CassandraQueriesSupport with Actor {
    def settings: CustomSettings
    def serialization: Serialization
  } =>
  import ChangesStream._

  val deserializer: (CassandraSource#Record, CassandraSource#Record) ⇒ (Any, Long) =
    (outer, inner) ⇒ {
      val rep = serialization.deserialize(Bytes.getArray(inner.getBytes("message")), classOf[PersistentRepr]).get
      (rep.payload, inner.getLong("sequence_nr"))
    }

  val qCampaign = for { q ← select("SELECT campaign_id FROM {0}") } yield q

  def qChanges(seqNum: Long)(r: Row) = for {
    _ ← select(s"select persistence_id, sequence_nr, message from {0} where persistence_id = ? and sequence_nr > $seqNum and partition_nr = 0")
    q ← fk[java.lang.String]("persistence_id", r.getString("campaign_id"))
  } yield q

  private def fetchChanges(seqNum: Long)(implicit client: CassandraSource#Client) =
    (Join[CassandraSource] left (qCampaign, campaignTable, qChanges(seqNum), settings.cassandra.table, settings.cassandra.keyspace))(deserializer)
      .source
      .filter(_._1.isInstanceOf[CampaignPersistedEvent])
      .map { kv =>
        //context.system.log.info(s"CampaignPersistedEvent has been fetched: $seqNum")
        val c = kv._1.asInstanceOf[CampaignPersistedEvent]
        PersistDataChange(kv._2, (c.results./:(Map[String, SortedSet[CreateResult]]()) { (map, res) ⇒
          val set = map.getOrElse(res.homeTeam, SortedSet[CreateResult]())
          val updated = set + CreateResult(res.homeTeam, res)
          map + (res.homeTeam -> updated)
        }))
      }

  def quorumClient = newClient(ConsistencyLevel.QUORUM)

  def changesStream(seqNum: Long, interval: FiniteDuration, client: CassandraSource#Client, des: ActorRef)(implicit Mat: ActorMaterializer): Unit =
    ((fetchChanges(seqNum)(client)) via readEvery(interval))
      .mapAsync(1) { ch => (des.ask(ch)(interval)).mapTo[Long] } //sort of back pressure
      .to(Sink.onComplete { _ =>
        (des.ask(GetLastChangeSetOffset)(interval)).mapTo[Long].map { n =>
          context.system.scheduler.scheduleOnce(interval, new Runnable {
            override def run() = changesStream(n, interval, client, des)
          })
        }
      }).run()(Mat)

}

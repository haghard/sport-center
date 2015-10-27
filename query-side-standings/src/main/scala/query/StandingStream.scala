package query

import akka.actor.{ ActorLogging, Actor, ActorRef }
import akka.persistence.PersistentRepr
import akka.serialization.Serialization
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import com.datastax.driver.core.{ ConsistencyLevel, Row }
import com.datastax.driver.core.utils.Bytes
import domain.TeamAggregate.ResultAdded
import domain.update.CassandraQueriesSupport
import dsl.cassandra._
import join.Join
import join.cassandra.CassandraSource
import microservice.crawler.NbaResultView
import microservice.settings.CustomSettings

import scala.concurrent.duration.FiniteDuration
import akka.pattern.ask

object StandingStream {
  val teamsTable = "teams"
  val qTeams = for { q ← select("SELECT processor_id FROM {0}") } yield q

  def qResults(seqNum: Long)(r: Row) = for {
    _ ← select(s"select persistence_id, sequence_nr, message from {0} where persistence_id = ? and sequence_nr > $seqNum and partition_nr = 0")
    q ← fk[java.lang.String]("persistence_id", r.getString("processor_id"))
  } yield q
}

trait StandingStream {
  mixin: CassandraQueriesSupport with Actor with ActorLogging {
    def settings: CustomSettings
    def serialization: Serialization
  } =>
  import StandingStream._

  def quorumClient = newClient(ConsistencyLevel.QUORUM)

  def deserializer: (CassandraSource#Record, CassandraSource#Record) ⇒ Any =
    (outer, inner) ⇒ {
      val rep = serialization.deserialize(Bytes.getArray(inner.getBytes("message")), classOf[PersistentRepr]).get
      rep.payload
    }

  private def fetchResult(seqNum: Long)(implicit client: CassandraSource#Client) =
    (Join[CassandraSource] left (qTeams, teamsTable, qResults(seqNum), settings.cassandra.table, settings.cassandra.keyspace))(deserializer)
      .source
      .filter(_.isInstanceOf[ResultAdded])
      .map { res =>
        val r = res.asInstanceOf[ResultAdded].r
        NbaResultView(r.homeTeam, r.homeScore, r.awayTeam, r.awayScore, r.dt, r.homeScoreBox, r.awayScoreBox)
      }

  def viewStream(seqNum: Long, interval: FiniteDuration, client: CassandraSource#Client, des: ActorRef, acc: Long, f: NbaResultView => Option[ActorRef])(implicit Mat: ActorMaterializer): Unit =
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

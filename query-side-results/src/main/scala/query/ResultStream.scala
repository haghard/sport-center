package query

import join.Join
import dsl.cassandra._
import akka.actor.{ Actor, ActorRef }
import akka.persistence.PersistentRepr
import akka.serialization.Serialization
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import com.datastax.driver.core.{ ConsistencyLevel, Row }
import com.datastax.driver.core.utils.Bytes
import domain.TeamAggregate.ResultAdded
import domain.update.CassandraQueriesSupport
import join.cassandra.CassandraSource
import microservice.crawler.NbaResult
import microservice.settings.CustomSettings
import scala.concurrent.duration.FiniteDuration
import akka.pattern.ask

object ResultStream {
  val teamsTable = "teams"

  val qTeams = for { q ← select("SELECT processor_id FROM {0}") } yield q

  def qResults(seqNum: Long)(r: Row) = for {
    _ ← select(s"select persistence_id, sequence_nr, message from {0} where persistence_id = ? and sequence_nr > $seqNum and partition_nr = 0")
    q ← fk[java.lang.String]("persistence_id", r.getString("processor_id"))
  } yield q
}

trait ResultStream {
  mixin: CassandraQueriesSupport with Actor {
    def settings: CustomSettings
    def serialization: Serialization
  } =>

  import ResultStream._

  def newQuorumClient = newClient(ConsistencyLevel.QUORUM)

  def deserializer: (CassandraSource#Record, CassandraSource#Record) ⇒ NbaResult =
    (outer, inner) ⇒ {
      val rep = serialization.deserialize(Bytes.getArray(inner.getBytes("message")), classOf[PersistentRepr]).get
      val domainEvent = rep.payload.asInstanceOf[ResultAdded]
      domainEvent.r
    }

  private def fetchResult(offset: Long)(implicit client: CassandraSource#Client) =
    (Join[CassandraSource] left (qTeams, teamsTable, qResults(offset), settings.cassandra.table, settings.cassandra.keyspace))(deserializer)
      .source

  def resultsStream(offset: Long, interval: FiniteDuration, client: CassandraSource#Client, des: ActorRef, acc: Long)(implicit Mat: ActorMaterializer): Unit = {
    (if (acc == 0) fetchResult(offset)(client) else fetchResult(offset)(client) via readEvery(interval))
      .map { res => des ! res }
      //.mapAsync(1) { res => (des.ask(res.size.toLong)(interval)).mapTo[Long] } //sort of back pressure
      .to(Sink.onComplete { _ =>
        (des.ask(offset)(interval)).mapTo[Long].map { n =>
          context.system.scheduler.scheduleOnce(interval, new Runnable {
            override def run() = resultsStream(n, interval, client, des, acc + 1l)
          })
        }
      }).run()(Mat)
  }
}
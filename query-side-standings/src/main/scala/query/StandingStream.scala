package query

import akka.actor.{ ActorLogging, Actor }
import akka.persistence.PersistentRepr
import akka.serialization.Serialization
import akka.stream.{ ClosedShape, Graph, SourceShape }
import akka.stream.scaladsl.{ Merge, FlowGraph, Source, Sink }
import com.datastax.driver.core.utils.Bytes
import com.datastax.driver.core.{ ConsistencyLevel }
import domain.TeamAggregate.ResultAdded
import domain.update.CassandraQueriesSupport
import join.cassandra.CassandraSource
import microservice.crawler.NbaResultView
import microservice.settings.CustomSettings

object StandingStream {
  /*
  val teamsTable = "teams"
  val qTeams = for { q ← select("SELECT processor_id FROM {0}") } yield q

  def qResults(seqNum: Long)(r: Row) = for {
    _ ← select(s"select persistence_id, sequence_nr, message from {0} where persistence_id = ? and sequence_nr > $seqNum and partition_nr = 0")
    q ← fk[java.lang.String]("persistence_id", r.getString("processor_id"))
  } yield q*/
}

trait StandingStream {
  mixin: CassandraQueriesSupport with Actor with ActorLogging {
    def settings: CustomSettings
    def serialization: Serialization
  } =>

  import FlowGraph.Implicits._

  def quorumClient = cassandraClient(ConsistencyLevel.QUORUM)

  private def flow(teams: Map[String, Int], journal: String)(implicit session: CassandraSource#Session) = Source.fromGraph(
    FlowGraph.create() { implicit b =>
      val merge = b.add(Merge[NbaResultView](teams.size))
      teams.foreach { kv =>
        feed.Feed[CassandraSource].from(queryByKey(journal), kv._1, kv._2)
          .source.map(row => serialization.deserialize(Bytes.getArray(row.getBytes("message")), classOf[PersistentRepr]).get.payload)
          .collect {
            case e: ResultAdded => NbaResultView(e.r.homeTeam, e.r.homeScore, e.r.awayTeam, e.r.awayScore, e.r.dt, e.r.homeScoreBox, e.r.awayScoreBox)
          } ~> merge
      }
      SourceShape(merge.out)
    }
  )

  def replayGraph(teams: Map[String, Int], journal: String)(implicit session: CassandraSource#Session): Graph[ClosedShape, Unit] = {
    FlowGraph.create() { implicit b =>
      flow(teams, journal) ~> Sink.actorRef[NbaResultView](self, 'RefreshCompleted)
      ClosedShape
    }
  }

  /*
  def deserializer: (CassandraSource#Record, CassandraSource#Record) ⇒ Any =
    (outer, inner) ⇒
      serialization.deserialize(Bytes.getArray(inner.getBytes("message")), classOf[PersistentRepr]).get.payload

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
        batch.foreach { r =>
          f(r).fold(log.debug("MaterializedView wasn't found for {}", r.dt))(_ ! r)
        }
      }
      .to(Sink.onComplete { _ =>
        (des.ask(seqNum)(interval)).mapTo[Long].map { n =>
          context.system.scheduler.scheduleOnce(interval, new Runnable {
            override def run() = viewStream(n, interval, client, des, acc + 1l, f)
          })
        }
      }).run()(Mat)*/
}
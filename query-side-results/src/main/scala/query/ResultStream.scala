package query

import akka.actor.Actor
import akka.persistence.PersistentRepr
import akka.serialization.Serialization
import com.datastax.driver.core.ConsistencyLevel
import akka.stream.{ Graph, ClosedShape, SourceShape }
import akka.stream.scaladsl.{ Merge, FlowGraph, Source, Sink }
import com.datastax.driver.core.utils.Bytes
import domain.TeamAggregate.ResultAdded
import domain.update.CassandraQueriesSupport
import join.cassandra.CassandraSource
import microservice.crawler.NbaResultView

trait ResultStream {
  mixin: CassandraQueriesSupport with Actor {
    def serialization: Serialization
  } =>
  import FlowGraph.Implicits._

  private def flow(teams: Map[String, Int], journal: String)(implicit session: CassandraSource#Session) = Source.fromGraph(
    FlowGraph.create() { implicit b =>
      val merge = b.add(Merge[NbaResultView](teams.size))
      teams.foreach { kv =>
        eventlog.Log[CassandraSource].from(queryByKey(journal), kv._1, kv._2)
          .source.map { row =>
            serialization.deserialize(Bytes.getArray(row.getBytes("message")), classOf[PersistentRepr]).get.payload
          }.collect {
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

  def newQuorumClient = cassandraClient(ConsistencyLevel.QUORUM)
}
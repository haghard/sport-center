package query

import akka.actor.Actor
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.PersistenceQuery
import akka.stream.{ ClosedShape, SourceShape }
import akka.stream.scaladsl.{ Merge, GraphDSL, Source, Sink }
import domain.TeamAggregate.ResultAdded
import microservice.crawler.NbaResultView

trait ResultsJournal {
  mixin: Actor =>

  import GraphDSL.Implicits._

  private def flow(teams: Map[String, Int], journal: String) /*(implicit session: CassandraSource#Session)*/ = Source.fromGraph(
    GraphDSL.create() { implicit b =>
      val merge = b.add(Merge[NbaResultView](teams.size))
      teams.foreach { kv =>
        PersistenceQuery(context.system)
          .readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)
          .eventsByPersistenceId(kv._1, kv._2, Long.MaxValue)
          .collect {
            case env if (env.event.isInstanceOf[ResultAdded]) =>
              val e = env.event.asInstanceOf[ResultAdded]
              NbaResultView(e.r.homeTeam, e.r.homeScore, e.r.awayTeam, e.r.awayScore, e.r.dt, e.r.homeScoreBox, e.r.awayScoreBox)
          } ~> merge

        /*
        import com.datastax.driver.core.utils.Bytes
        eventlog.Log[CassandraSource].from(queryByKey(journal), kv._1, kv._2)
          .source.map { row =>
            serialization.deserialize(Bytes.getArray(row.getBytes("message")), classOf[PersistentRepr]).get.payload
          }.collect {
            case e: ResultAdded => NbaResultView(e.r.homeTeam, e.r.homeScore, e.r.awayTeam, e.r.awayScore, e.r.dt, e.r.homeScoreBox, e.r.awayScoreBox)
          } ~> merge
        */
      }
      SourceShape(merge.out)
    }
  )

  def replayGraph(teams: Map[String, Int], journal: String) = {
    GraphDSL.create() { implicit b =>
      flow(teams, journal) ~> Sink.actorRef[NbaResultView](self, 'RefreshCompleted)
      ClosedShape
    }
  }
}
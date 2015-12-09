package view

import akka.stream.scaladsl.RunnableGraph
import query.ResultStream
import akka.actor.{ Props, Actor, ActorLogging }
import akka.serialization.SerializationExtension
import domain.update.CassandraQueriesSupport
import microservice.crawler.{ NbaResultView, Location }
import microservice.domain.State
import microservice.http.RestService.ResponseBody
import microservice.settings.CustomSettings
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import akka.stream._
import http.ResultsMicroservice.{ GetResultsByTeam, GetResultsByDate }

object ResultViewRouter {
  case class ResultsByTeamBody(count: Int = 0, results: List[NbaResultView]) extends ResponseBody with State
  case class ResultsByDateBody(count: Int = 0, results: ArrayBuffer[NbaResultView]) extends ResponseBody with State

  def props(settings: CustomSettings) = Props(classOf[ResultViewRouter], settings)
}

class ResultViewRouter private (val settings: CustomSettings) extends Actor with ActorLogging
    with ResultStream with CassandraQueriesSupport {
  import ResultViewRouter._
  var updateCnt = 0
  val decider: Supervision.Decider = {
    case ex ⇒
      log.error(ex, "Results fetch error")
      Supervision.stop
  }

  val serialization = SerializationExtension(context.system)
  private val formatter = microservice.crawler.searchFormatter()
  private val viewByDate = mutable.HashMap[String, ArrayBuffer[NbaResultView]]()
  private val viewByTeam = mutable.HashMap[String, mutable.SortedSet[NbaResultView]]()

  val client = newQuorumClient
  val tryToRefreshEvery = settings.refreshIntervals.resultsPeriod
  var offsets = settings.teamConferences.keySet./:(Map.empty[String, Int])((map, c) => map + (c -> 0))

  implicit var session = (newQuorumClient connect settings.cassandra.keyspace)

  implicit val Mat = ActorMaterializer(ActorMaterializerSettings(context.system)
    .withDispatcher("stream-dispatcher")
    .withSupervisionStrategy(decider)
    .withInputBuffer(32, 64))(context.system)

  override def preStart() = {
    context.system.scheduler.scheduleOnce(tryToRefreshEvery)(
      RunnableGraph.fromGraph(replayGraph(offsets, settings.cassandra.table)).run()(Mat)
    )
  }

  override def receive: Receive = {
    case r: NbaResultView =>
      updateCnt += 1
      val date = formatter format r.dt
      viewByDate.get(date).fold { viewByDate += (date -> ArrayBuffer[NbaResultView](r)); () } { res => res += r }
      viewByTeam.get(r.homeTeam).fold { viewByTeam += (r.homeTeam -> mutable.SortedSet[NbaResultView](r)); () } { res => res += r }
      viewByTeam.get(r.awayTeam).fold { viewByTeam += (r.awayTeam -> mutable.SortedSet[NbaResultView](r)); () } { res => res += r }
      offsets = offsets.updated(r.homeTeam, offsets(r.homeTeam) + 1)

    case 'RefreshCompleted =>
      log.info("ResultView:{} changes have been discovered", updateCnt)
      updateCnt = 0
      context.system.scheduler.scheduleOnce(tryToRefreshEvery)(RunnableGraph.fromGraph(replayGraph(offsets, settings.cassandra.table)).run()(Mat))

    case GetResultsByDate(uri, date) =>
      sender() ! viewByDate.get(date).fold(ResultsByDateBody(0, ArrayBuffer[NbaResultView]())) { list => ResultsByDateBody(list.size, list) }

    case GetResultsByTeam(uri, team, size, location) =>
      location match {
        case Location.All ⇒
          val list = viewByTeam.get(team).map(_.takeRight(size))
          sender() ! list.fold(ResultsByTeamBody(0, List())) { res => ResultsByTeamBody(res.size, res.toList) }

        case Location.Home ⇒
          viewByTeam.get(team).map(_.foldRight(List[NbaResultView]()) { (c, acc) ⇒
            if (c.homeTeam == team && acc.size < size) c :: acc
            else acc
          }).fold(sender() ! ResultsByTeamBody(0, List())) { results =>
            sender() ! ResultsByTeamBody(results.size, results)
          }

        case Location.Away ⇒
          viewByTeam.get(team).map(_.foldRight(List[NbaResultView]()) { (c, acc) ⇒
            if (c.awayTeam == team && acc.size < size) c :: acc
            else acc
          }).fold(sender() ! ResultsByTeamBody(0, List())) { results =>
            sender() ! ResultsByTeamBody(results.size, results)
          }
      }
  }
}
package view

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
import akka.stream.{ Supervision, ActorMaterializerSettings, ActorMaterializer }
import http.ResultsMicroservice.{ GetResultsByTeam, GetResultsByDate }
import scala.concurrent.duration._

object ResultViewRouter {
  case class ResultsByTeamBody(count: Int = 0, results: List[NbaResultView]) extends ResponseBody with State
  case class ResultsByDateBody(count: Int = 0, results: ArrayBuffer[NbaResultView]) extends ResponseBody with State

  def props(settings: CustomSettings) = Props(classOf[ResultViewRouter], settings)
}

class ResultViewRouter private (val settings: CustomSettings) extends Actor with ActorLogging
    with ResultStream with CassandraQueriesSupport {
  import ResultViewRouter._

  val decider: Supervision.Decider = {
    case ex ⇒
      log.error(ex, "Results fetch error")
      Supervision.stop
  }

  implicit val Mat = ActorMaterializer(ActorMaterializerSettings(context.system)
    .withDispatcher("stream-dispatcher")
    .withSupervisionStrategy(decider)
    .withInputBuffer(32, 64))(context.system)

  val serialization = SerializationExtension(context.system)
  private val formatter = microservice.crawler.searchFormatter()
  private val viewByDate = mutable.HashMap[String, ArrayBuffer[NbaResultView]]()
  private val viewByTeam = mutable.HashMap[String, mutable.SortedSet[NbaResultView]]()

  var offset = 0l
  val client = newQuorumClient
  val refreshEvery = 20 seconds

  override def preStart() =
    resultsStream(offset, refreshEvery, client, self, 0)

  override def receive: Receive = {
    case r: NbaResultView =>
      val date = formatter format r.dt
      viewByDate.get(date).fold { viewByDate += (date -> ArrayBuffer[NbaResultView](r)); () } { res => res += r }
      viewByTeam.get(r.homeTeam).fold { viewByTeam += (r.homeTeam -> mutable.SortedSet[NbaResultView](r)); () } { res => res += r }
      viewByTeam.get(r.awayTeam).fold { viewByTeam += (r.awayTeam -> mutable.SortedSet[NbaResultView](r)); () } { res => res += r }
      offset += 1

    case seqNum: Long =>
      log.info("ResultView offset №{}", offset)
      sender() ! offset

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
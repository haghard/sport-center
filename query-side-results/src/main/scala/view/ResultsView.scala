package view

import scala.collection._
import scalaz.concurrent.Task
import microservice.domain.State
import domain.TeamAggregate.ResultAdded
import scala.collection.mutable.ArrayBuffer
import akka.actor.{ Props, ActorLogging, Actor }
import microservice.crawler.{ NbaResult, Location }
import microservice.http.RestService.ResponseBody
import microservice.settings.CustomSettings
import streamz.akka.persistence.Event
import http.ResultsMicroservice.{ GetResultsByTeam, GetResultsByDate }

object ResultsView {

  implicit val strategy = scalaz.concurrent.Strategy.Executor(
    microservice.executor("results-materialized-view-executor", 2))

  implicit object Ord extends Ordering[NbaResult] {
    override def compare(x: NbaResult, y: NbaResult): Int = x.dt.compareTo(y.dt)
  }

  case class ResultsByDateBody(count: Int = 0, results: ArrayBuffer[NbaResult]) extends ResponseBody with State

  case class ResultsByTeamBody(count: Int = 0, results: List[NbaResult]) extends ResponseBody with State

  def props(settings: CustomSettings) = Props(new ResultsView(settings))
}

class ResultsView private (settings: CustomSettings) extends Actor with ActorLogging {
  import ResultsView._
  import scalaz.stream._
  import streamz.akka._
  import scalaz.stream.Process._

  private val formatter = microservice.crawler.searchFormatter()
  private val viewByDate = mutable.HashMap[String, ArrayBuffer[NbaResult]]()
  private val viewByTeam = mutable.HashMap[String, mutable.SortedSet[NbaResult]]()

  private val homeFilter: (Event[Any] ⇒ Boolean) = x ⇒
    x.data.isInstanceOf[ResultAdded] && x.data.asInstanceOf[ResultAdded].r.lct == Location.Home

  private def subscriber(domainActorName: String): Process[Task, NbaResult] =
    persistence.replay(domainActorName)(context.system)
      .filter(homeFilter) map { x ⇒
        val res = x.data.asInstanceOf[ResultAdded].r
        NbaResult(domainActorName, res.homeScore, res.opponent, res.awayScore, res.dt)
      }

  private val sink: Sink[Task, NbaResult] =
    io.channel(result ⇒ Task.delay { self ! result })

  override def preStart = {
    (merge.mergeN(emitAll(settings.teams) |> process1.lift(subscriber))
      .to(sink))
      .run.runAsync(_ => ())
  }

  override def receive: Receive = {
    case r: NbaResult =>
      val date = formatter format r.dt
      viewByDate.get(date).fold { viewByDate += (date -> ArrayBuffer[NbaResult](r)); () } { res => res += r }
      viewByTeam.get(r.homeTeam).fold { viewByTeam += (r.homeTeam -> mutable.SortedSet[NbaResult](r)); () } { res => res += r }
      viewByTeam.get(r.roadTeam).fold { viewByTeam += (r.roadTeam -> mutable.SortedSet[NbaResult](r)); () } { res => res += r }

    case GetResultsByDate(uri, date) =>
      sender() ! viewByDate.get(date).fold(ResultsByDateBody(0, new ArrayBuffer[NbaResult]())) { list => ResultsByDateBody(list.size, list) }

    case GetResultsByTeam(uri, team, size, location) =>
      location match {
        case Location.All ⇒
          val list = viewByTeam.get(team).map(_.takeRight(size))
          sender() ! list.fold(ResultsByTeamBody(0, List())) { res => ResultsByTeamBody(res.size, res.toList) }

        case Location.Home ⇒
          viewByTeam.get(team).map(_.foldRight(List[NbaResult]()) { (c, acc) ⇒
            if (c.homeTeam == team && acc.size < size) c :: acc
            else acc
          }).fold(sender() ! ResultsByTeamBody(0, List())) { results =>
            sender() ! ResultsByTeamBody(results.size, results)
          }

        case Location.Away ⇒
          viewByTeam.get(team).map(_.foldRight(List[NbaResult]()) { (c, acc) ⇒
            if (c.roadTeam == team && acc.size < size) c :: acc
            else acc
          }).fold(sender() ! ResultsByTeamBody(0, List())) { results =>
            sender() ! ResultsByTeamBody(results.size, results)
          }
      }
  }
}
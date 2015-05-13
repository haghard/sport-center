package view

import scala.collection._
import scalaz.concurrent.Task
import scalaz.concurrent._
import microservice.domain.State
import domain.TeamAggregate.ResultAdded
import scala.collection.mutable.ArrayBuffer
import akka.actor.{ Props, ActorLogging, Actor }
import microservice.crawler.{ NbaResult, Location }
import microservice.http.RestService.ResponseBody
import microservice.settings.CustomSettings
import http.ResultsMicroservice.{ GetResultsByTeam, GetResultsByDate }

object ResultsViewRouter {

  implicit val strategy = Strategy.Executor(microservice.executor("results-view-executor", 2))

  implicit object Ord extends Ordering[NbaResult] {
    override def compare(x: NbaResult, y: NbaResult): Int =
      x.dt.compareTo(y.dt)
  }

  case class ResultsByDateBody(count: Int = 0, results: ArrayBuffer[NbaResult]) extends ResponseBody with State
  case class ResultsByTeamBody(count: Int = 0, results: List[NbaResult]) extends ResponseBody with State

  def props(settings: CustomSettings) = Props(new ResultsViewRouter(settings))
}

class ResultsViewRouter private (settings: CustomSettings) extends Actor with ActorLogging {
  import ResultsViewRouter._

  private val formatter = microservice.crawler.searchFormatter()
  private val viewByDate = mutable.HashMap[String, ArrayBuffer[NbaResult]]()
  private val viewByTeam = mutable.HashMap[String, mutable.SortedSet[NbaResult]]()

  private def subscriber(domainActorName: String): scalaz.stream.Process[Task, NbaResult] =
    streamz.akka.persistence.replay(domainActorName)(context.system).map(_.data.asInstanceOf[ResultAdded].r)

  private val sink: scalaz.stream.Sink[Task, NbaResult] =
    scalaz.stream.io.channel(result ⇒ Task.delay { self ! result })

  override def preStart =
    (scalaz.stream.merge.mergeN(scalaz.stream.Process.emitAll(settings.teams) |> scalaz.stream.process1.lift(subscriber))
      .to(sink)).run.runAsync(_ => ())

  override def receive: Receive = {
    case r: NbaResult =>
      val date = formatter format r.dt
      viewByDate.get(date).fold { viewByDate += (date -> ArrayBuffer[NbaResult](r)); () } { res => res += r }
      viewByTeam.get(r.homeTeam).fold { viewByTeam += (r.homeTeam -> mutable.SortedSet[NbaResult](r)); () } { res => res += r }
      viewByTeam.get(r.awayTeam).fold { viewByTeam += (r.awayTeam -> mutable.SortedSet[NbaResult](r)); () } { res => res += r }

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
            if (c.awayTeam == team && acc.size < size) c :: acc
            else acc
          }).fold(sender() ! ResultsByTeamBody(0, List())) { results =>
            sender() ! ResultsByTeamBody(results.size, results)
          }
      }
  }
}
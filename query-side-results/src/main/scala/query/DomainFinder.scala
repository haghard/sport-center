package query

import akka.actor.ActorDSL._
import akka.actor._
import domain.Domains
import domain.TeamAggregate.{ QueryTeamStateByDate, QueryTeamStateLast, TeamStateSet, TeamStateSingle }
import http.ResultsMicroservice._
import microservice.api.MicroserviceKernel
import microservice.crawler.NbaResult
import microservice.domain.State
import microservice.http.RestService.ResponseBody
import microservice.settings.CustomSettings

import scala.collection._

import query.DomainFinder._

object DomainFinder {

  case class ResultsBody(count: Int = 0, list: immutable.List[NbaResult] = immutable.List())
    extends ResponseBody with State

  def error(team: String) = s"Team $team not exists"

  private[DomainFinder] def aggregator(replyTo: Option[ActorRef], url: String, n: Int)(implicit factory: ActorRefFactory) =
    actor(new Act {
      var localN = n
      var buffer = immutable.List[NbaResult]()
      become {
        case state: TeamStateSingle ⇒
          state.res.foreach(r ⇒ buffer = buffer :+ r)
          localN -= 1
          if (localN == 0) {
            replyTo foreach (_ ! ResultsBody(buffer.size, buffer))
          }
        case other: String ⇒
          localN -= 1
          if (localN == 0) {
            replyTo foreach (_ ! ResultsBody(0, List()))
          }
      }
    })

  private[DomainFinder] def responder(replyTo: Option[ActorRef], url: String, desc: String)(implicit factory: ActorRefFactory) =
    actor(new Act {
      become {
        case TeamStateSet(_, results) ⇒
          replyTo foreach (_ ! ResultsBody(results.size, results))
        case other: String ⇒
          replyTo foreach (_ ! ResultsBody(0, List()))
      }
    })

  def props(settings: CustomSettings) =
    Props(new DomainFinder(settings)).withDispatcher(MicroserviceKernel.microserviceDispatcher)
}

class DomainFinder private (settings: CustomSettings) extends Actor
    with ActorLogging {

  private var replyTo: Option[ActorRef] = None

  override def preRestart(reason: scala.Throwable, message: scala.Option[scala.Any]) = {
    log.info("{} was restarted cause: {}", self, reason.getMessage)
    message match {
      case Some(GetResultsByDate(url, d)) ⇒
        replyTo foreach (_ ! ResultsResponse(url, error = Some(reason.getMessage)))
      case Some(GetResultsByTeam(url, team, size, location)) ⇒
        replyTo foreach (_ ! ResultsResponse(url, error = Some(reason.getMessage)))
      case None ⇒
    }
  }

  override def receive: Receive = {
    case r @ GetResultsByDate(url, searchDT) ⇒
      replyTo = Some(sender())
      implicit val handler = aggregator(replyTo, url, settings.teams.size)
      settings.teams.foreach { team ⇒
        Domains(context.system).tellQuery(QueryTeamStateByDate(team, searchDT))(handler)
      }

    case r @ GetResultsByTeam(url, team, size, l) ⇒
      replyTo = Some(sender())
      val desc = s"last-$l-$size-for-$team"
      val handler = responder(replyTo, url, desc)
      settings.teams.find(_ == team).fold(sender() ! ResultsResponse(url, Some(desc), error = Some(error(team)))) { t ⇒
        Domains(context.system).tellQuery(QueryTeamStateLast(t, size, l))(handler)
      }
  }
}
package query

import org.joda.time.DateTime
import akka.actor.ActorDSL._
import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import http.StandingMicroservice.GetStandingByDate
import microservice.domain.{ QueryCommand, State }
import microservice.http.RestService.ResponseBody
import microservice.settings.CustomSettings
import query.StandingMaterializedView.{ PlayOffStandingResponse, SeasonStandingResponse }
import microservice.crawler.searchFormatter

import scalaz.{ -\/, \/, \/- }

object StandingTopView {
  val season = "season-view"
  val playoff = "playoff-view"

  case class StandingBody(val body: Option[SeasonStandingResponse \/ PlayOffStandingResponse] = None,
                          viewName: Option[String] = None, error: Option[String] = None, val count: Int = 0)
      extends ResponseBody with State

  case class QueryStandingByDate(date: DateTime) extends QueryCommand

  def props(settings: CustomSettings): Props =
    Props(new StandingTopView(settings))
}

class StandingTopView private (val settings: CustomSettings) extends Actor
    with ActorLogging
    with MaterializedViewStreamSupport {
  import query.StandingTopView._

  private val formatter = searchFormatter()

  private def receiver(replyTo: ActorRef) = actor(new Act {
    become {
      case error: String ⇒
        replyTo ! StandingBody(error = Option(error))
        context.stop(self)
      case resp: SeasonStandingResponse ⇒
        replyTo ! StandingBody(viewName = Option(season), body = Some(-\/(resp)))
        context.stop(self)
      case resp: PlayOffStandingResponse ⇒
        replyTo ! StandingBody(viewName = Option(playoff), body = Some(\/-(resp)))
        context.stop(self)
    }
  })

  override def receive: Receive = {
    case GetStandingByDate(uri, dateTime) ⇒
      getChildView(dateTime).fold {
        sender() ! StandingBody(error =
          Option(s"Can't find view for requested date ${formatter format dateTime}"))
      } { view ⇒ view.tell(QueryStandingByDate(dateTime), receiver(sender())) }
  }
}
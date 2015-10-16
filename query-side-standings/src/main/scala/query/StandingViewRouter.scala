package query

import akka.serialization.SerializationExtension
import akka.stream.{ ActorMaterializerSettings, ActorMaterializer, Supervision }
import domain.update.CassandraQueriesSupport
import org.joda.time.DateTime
import akka.actor.ActorDSL._
import scala.collection.immutable
import scalaz.{ -\/, \/, \/- }
import microservice.settings.CustomSettings
import microservice.crawler.searchFormatter
import microservice.http.RestService.ResponseBody
import http.StandingMicroservice.GetStandingByDate
import microservice.domain.{ QueryCommand, State }
import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import query.StandingMaterializedView.{ PlayOffStandingResponse, SeasonStandingResponse }

object StandingViewRouter {
  val season = "season-view"
  val playoff = "playoff-view"

  case class StandingBody(val body: Option[SeasonStandingResponse \/ PlayOffStandingResponse] = None,
    viewName: Option[String] = None, error: Option[String] = None, val count: Int = 0)
      extends ResponseBody with State

  case class QueryStandingByDate(date: DateTime) extends QueryCommand

  def viewName(name: String): String = s"materialized-view-$name"

  def props(settings: CustomSettings): Props = Props(new StandingViewRouter(settings))
}

class StandingViewRouter private (val settings: CustomSettings) extends Actor with ActorLogging
    with CassandraQueriesSupport {
  import scala.concurrent.duration._
  import query.StandingViewRouter._

  var seqNumber = 0l
  val default = 30 seconds
  val formatter = searchFormatter()
  val serialization = SerializationExtension(context.system)

  val decider: Supervision.Decider = {
    case ex ⇒
      log.error(ex, "StandingView fetch error")
      Supervision.stop
  }

  implicit val Mat = ActorMaterializer(ActorMaterializerSettings(context.system)
    .withDispatcher("stream-dispatcher")
    .withSupervisionStrategy(decider)
    .withInputBuffer(32, 64))(context.system)

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

  override def preStart() = pull()

  override def receive: Receive = {
    case sn: Long =>
      log.info("StandingView sequence number №{}", sn)
      sender() ! seqNumber
    case GetStandingByDate(uri, dateTime) ⇒
      getChildView(dateTime).fold {
        val error = s"Can't find view for requested date ${formatter.format(dateTime.toDate)}"
        log.error(error)
        sender() ! StandingBody(error = Option(error))
      } { view ⇒ view.tell(QueryStandingByDate(dateTime), receiver(sender())) }
  }

  private def getChildView(dt: DateTime): Option[ActorRef] = {
    (for {
      (interval, intervalName) ← settings.intervals
      if interval.contains(dt)
    } yield intervalName).headOption
      .flatMap { v ⇒
        childViews.get(viewName(v))
      }
  }

  private val childViews: immutable.Map[String, ActorRef] =
    settings.stages.foldLeft(immutable.Map[String, ActorRef]()) { (map, c) ⇒
      val vName = viewName(c._1)
      val view = context.actorOf(StandingMaterializedView.props(settings), name = vName)
      log.info("{} was created", vName)
      map + (vName -> view)
    }

  private def pull() = {
    viewStream(0l, default, newClient, self, 0, { result =>
      seqNumber += 1l
      getChildView(new DateTime(result.dt))
    })
  }
}
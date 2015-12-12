package query

import akka.stream.javadsl.RunnableGraph
import akka.stream.{ ActorMaterializerSettings, Supervision, ActorMaterializer }
import org.joda.time.DateTime
import akka.actor.ActorDSL._
import scala.collection.immutable
import scalaz.{ -\/, \/, \/- }
import microservice.settings.CustomSettings
import microservice.crawler.{ NbaResultView, searchFormatter }
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
    with StandingStream /*with CassandraQueriesSupport*/ {
  import query.StandingViewRouter._

  var updateCnt = 0
  val tryToRefreshEvery = settings.refreshIntervals.standingsPeriod
  val formatter = searchFormatter()
  var offsets = settings.teamConferences.keySet.foldLeft(Map.empty[String, Int])((map, c) => map + (c -> 0))

  val decider: Supervision.Decider = {
    case ex ⇒
      log.error(ex, "StandingView fetch error")
      Supervision.stop
  }

  implicit val ctx = context.system.dispatchers.lookup("stream-dispatcher")
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

  override def preStart() =
    RunnableGraph.fromGraph(replayGraph(offsets, settings.cassandra.table)).run(Mat)

  override def receive: Receive = {
    case r: NbaResultView =>
      updateCnt += 1
      viewPartition(new DateTime(r.dt)).fold(log.debug("MaterializedView wasn't found for {}", r.dt))(_ ! r)
      offsets = (offsets updated (r.homeTeam, offsets(r.homeTeam) + 1))

    case 'RefreshCompleted =>
      log.info("ResultView: {} changes have been discovered", updateCnt)
      updateCnt = 0
      context.system.scheduler.scheduleOnce(tryToRefreshEvery)(RunnableGraph.fromGraph(replayGraph(offsets, settings.cassandra.table)).run(Mat))

    case GetStandingByDate(uri, dateTime) ⇒
      viewPartition(dateTime).fold {
        val error = s"Can't find view for requested date ${formatter.format(dateTime.toDate)}"
        log.error(error)
        sender() ! StandingBody(error = Option(error))
      } { view ⇒ view.tell(QueryStandingByDate(dateTime), receiver(sender())) }
  }

  private def viewPartition(dt: DateTime): Option[ActorRef] =
    (for {
      (interval, intervalName) ← settings.intervals
      if interval.contains(dt)
    } yield intervalName).headOption
      .flatMap(v ⇒ childViews.get(viewName(v)))

  private val childViews: immutable.Map[String, ActorRef] =
    settings.stages.foldLeft(immutable.Map[String, ActorRef]()) { (map, c) ⇒
      val vName = viewName(c._1)
      val view = context.actorOf(StandingMaterializedView.props(settings), name = vName)
      log.info("{} was created", vName)
      map + (vName -> view)
    }
}
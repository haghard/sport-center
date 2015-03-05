package http

import java.io.File
import java.nio.file.Paths

import akka.http.model.HttpResponse
import akka.http.server.Route
import com.netflix.config.DynamicPropertyFactory
import discovery.DiscoveryClientSupport
import microservice.api.MicroserviceKernel
import microservice.crawler.NbaResult
import microservice.http.RestService.{ BasicHttpRequest, BasicHttpResponse, ResponseBody }
import microservice.http.RestWithDiscovery._
import microservice.http.{ RestApiJunction, RestWithDiscovery }
import microservice.{ AskManagment, SystemSettings }
import org.joda.time.DateTime
import query.StandingMaterializedView.{ PlayOffStandingResponse, SeasonMetrics, SeasonStandingResponse, StandingLine }
import query.StandingTopView
import query.StandingTopView.StandingBody
import spray.json._

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }
import scalaz.{ -\/, \/- }
import scala.concurrent.duration._
import microservice.crawler.searchFormatter

object StandingMicroservice {

  case class GetStandingByDate(url: String, date: DateTime) extends BasicHttpRequest

  case class StandingsResponse(val url: String, val view: Option[String] = None, val error: Option[String] = None,
    val body: Option[ResponseBody] = None) extends BasicHttpResponse

  implicit object StandingResponseWriter extends JsonWriter[StandingsResponse] with DefaultJsonProtocol {
    import spray.json._

    implicit val jsonFormatResults = jsonFormat5(NbaResult)
    implicit val jsonFormatMetrics = jsonFormat7(SeasonMetrics)
    implicit val jsonFormatLine = jsonFormat2(StandingLine)

    override def write(obj: StandingsResponse): JsValue = {
      obj.body match {
        case Some(response) ⇒ response match {
          case r: SeasonStandingResponse  ⇒ JsObject("west-conf" -> r.west.toJson, "east-conf" -> r.east.toJson, "count" -> JsNumber(r.west.size))
          case r: PlayOffStandingResponse ⇒ JsObject("stages" -> r.stages.toMap.toJson, "count" -> JsNumber(r.stages.keySet.size))
        }
        case None ⇒ JsString(obj.error.get)
      }
    }
  }

  val hystrixSettings = Paths.get(new File("").getAbsoluteFile + "/query-side-standings/settings/archaius.properties")
  System.setProperty("archaius.fixedDelayPollingScheduler.delayMills", "1000")
  System.setProperty("archaius.fixedDelayPollingScheduler.initialDelayMills", "1000")
  System.setProperty("archaius.configurationSource.additionalUrls", "file:///" + hystrixSettings.toString)

  private val standingsProps = "hystrix.api.standings.injectable.latency"
  private val standingsLatency = DynamicPropertyFactory.getInstance().getLongProperty(standingsProps, 0)
}

trait StandingMicroservice extends RestWithDiscovery
    with SystemSettings
    with AskManagment {
  mixin: MicroserviceKernel with DiscoveryClientSupport ⇒
  import StandingMicroservice._

  private val formatter = searchFormatter()

  override def name = "StandingMicroservice"

  override lazy val servicePathPostfix = "standings"

  override implicit val timeout = akka.util.Timeout(3 seconds)

  override lazy val endpoints = List(s"$httpPrefixAddress/$pathPrefix/$servicePathPostfix/{dt}")

  private lazy val standingView = system.actorOf(StandingTopView.props(settings), name = "standing-top-view")

  abstract override def configureApi() =
    super.configureApi() ~
      RestApiJunction(route = Option { ec: ExecutionContext ⇒ standingRoute(ec) },
        preAction = Option(() ⇒ system.log.info(s"\n★ ★ ★  [$name] was started on $httpPrefixAddress ★ ★ ★")),
        postAction = Option(() ⇒ system.log.info(s"\n★ ★ ★  [$name] was stopped on $httpPrefixAddress ★ ★ ★")))

  private def standingRoute(implicit ec: ExecutionContext): Route = {
    pathPrefix(pathPrefix) {
      (get & path(servicePathPostfix / Segment)) { date ⇒
        withUri { uri ⇒
          complete {
            system.log.info(s"[$name] - incoming request $uri")
            //for latency injection
            Thread.sleep(standingsLatency.get())
            Try {
              new DateTime(formatter parse date)
            } match {
              case Success(dt)    ⇒ compete(uri, dt)
              case Failure(error) ⇒ fail(StandingsResponse(uri, error = Option(error.getMessage))).apply(error.getMessage)
            }
          }
        }
      }
    }
  }

  private def compete(uri: String, dt: DateTime)(implicit ex: ExecutionContext): Future[HttpResponse] =
    fetch[StandingBody](GetStandingByDate(uri, dt), standingView).map {
      case \/-(body) ⇒
        body.error.fold {
          body.body match {
            case Some(\/-(b)) ⇒ success(StandingsResponse(uri, view = body.viewName, body = Option(b)))
            case Some(-\/(b)) ⇒ success(StandingsResponse(uri, view = body.viewName, body = Option(b)))
          }
        } { error ⇒ fail(error) }
      case -\/(error) ⇒ fail(error)
    }
}
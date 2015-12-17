package http

import java.util.concurrent.TimeUnit
import akka.cluster.sharding.ShardRegion.CurrentRegions
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.server.Route
import com.netflix.config.DynamicPropertyFactory
import discovery.DiscoveryClientSupport
import domain.ShardedDomain
import microservice.api.MicroserviceKernel
import microservice.crawler._
import microservice.http.RestService.{ BasicHttpRequest, BasicHttpResponse, ResponseBody }
import microservice.http.ShardedDomainReadService._
import microservice.http.{ ShardedDomainReadService, RestApiJunction }
import microservice.{ AskSupport, SystemSettings }
import org.joda.time.DateTime
import query.StandingViewRouter
import query.StandingViewRouter.StandingBody
import spray.json._
import query.StandingMaterializedView.{ PlayOffStandingResponse, SeasonMetrics, SeasonStandingResponse, StandingLine }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }
import scalaz.{ -\/, \/- }

object StandingMicroservice {

  case class GetStandingByDate(url: String, date: DateTime) extends BasicHttpRequest

  case class StandingsResponse(val url: String, val view: Option[String] = None, val error: Option[String] = None,
    val body: Option[ResponseBody] = None) extends BasicHttpResponse

  case class Shards(count: Int = 0, results: List[String]) extends ResponseBody

  implicit object StandingResponseWriter extends JsonWriter[StandingsResponse] with DefaultJsonProtocol {
    import spray.json._
    implicit val viewFormat = jsonFormat7(NbaResultView)
    implicit val jsonFormatMetrics = jsonFormat7(SeasonMetrics)
    implicit val jsonFormatLine = jsonFormat2(StandingLine)

    override def write(obj: StandingsResponse): JsValue = {
      obj.body match {
        case Some(response) ⇒ response match {
          case r: SeasonStandingResponse  ⇒ JsObject("west-conf" -> r.west.toJson, "east-conf" -> r.east.toJson, "count" -> JsNumber(r.west.size))
          case r: PlayOffStandingResponse ⇒ JsObject("stages" -> r.stages.toMap.toJson, "count" -> JsNumber(r.stages.keySet.size))
          case r: Shards                  => JsObject("results" -> JsArray(r.results.toJson), "count" -> JsNumber(r.results.size))
        }
        case None ⇒ JsString(obj.error.get)
      }
    }
  }

  private val standingsProps = "hystrix.api.standings.injectable.latency"
  private val standingsLatency = DynamicPropertyFactory.getInstance().getLongProperty(standingsProps, 0)
}

trait StandingMicroservice extends ShardedDomainReadService
    with SystemSettings
    with AskSupport {
  mixin: MicroserviceKernel with DiscoveryClientSupport ⇒
  import StandingMicroservice._

  private val formatter = searchFormatter()

  override def name = "StandingMicroservice"

  override lazy val servicePathPostfix = "standings"

  implicit override val timeout = akka.util.Timeout(settings.timeouts.standings.getSeconds, TimeUnit.SECONDS)

  override lazy val endpoints = s"$httpPrefixAddress/$pathPref/$servicePathPostfix/{dt}" :: Nil

  private lazy val standingView = system.actorOf(StandingViewRouter.props(settings), "standing-top-view")

  abstract override def configureApi() =
    super.configureApi() ~
      RestApiJunction(route = Option { ec: ExecutionContext ⇒ standingRoute(ec) },
        preAction = Option { () ⇒
          ShardedDomain(system).start()
          system.log.info(s"\n★ ★ ★  [$name] was started on $httpPrefixAddress ★ ★ ★")
        },
        postAction = Option(() ⇒ system.log.info(s"\n★ ★ ★  [$name] was stopped on $httpPrefixAddress ★ ★ ★")))

  private def standingRoute(implicit ec: ExecutionContext): Route = {
    pathPrefix(pathPref) {
      path(servicePathPostfix / Segment) { date ⇒
        get {
          requiredHttpSession(ec) { session ⇒
            withUri { uri ⇒
              complete {
                system.log.info(s"[$name][$session] - incoming request $uri")
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
    } ~ path("showShardRegions") {
      get {
        withUri(uri ⇒ complete(readRegions(uri)))
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

  private def readRegions(uri: String)(implicit ex: ExecutionContext): Future[HttpResponse] = {
    domain.ShardedDomain(system).showRegions.map { adds: CurrentRegions =>
      success(StandingsResponse(uri, view = Option("shard-regions"),
        body = Option(Shards(adds.regions.size, adds.regions.map(_.toString).toList))))
    }.recoverWith {
      case e: Throwable =>
        fail(StandingsResponse(uri, view = Option("shard-regions"), error = Option(e.getMessage))).apply(e.getMessage)
    }
  }
}
package http

import java.util.concurrent.TimeUnit
import akka.cluster.sharding.ShardRegion.CurrentRegions
import akka.http.scaladsl.model.HttpResponse
import com.netflix.config.DynamicPropertyFactory
import discovery.DiscoveryClientSupport
import domain.{ ShardedDomain, DomainSupport }
import http.ResultsMicroservice._
import microservice.api.MicroserviceKernel
import microservice.crawler._
import microservice.http.ShardedDomainReadService.DateFormatToJson
import microservice.http.{ ShardedDomainReadService, RestApiJunction, ShardedDomainReadService$ }
import microservice.{ AskSupport, SystemSettings }
import spray.json._
import microservice.http.RestService.{ BasicHttpRequest, BasicHttpResponse, ResponseBody }
import view.ResultViewRouter
import view.ResultViewRouter.{ ResultsByTeamBody, ResultsByDateBody }
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }
import scalaz.{ -\/, \/- }
import akka.http.scaladsl.server._
import com.softwaremill.session.SessionDirectives._

object ResultsMicroservice {
  case class GetResultsByDate(url: String, dt: String) extends BasicHttpRequest
  case class GetResultsByTeam(url: String, name: String, size: Int, location: Location.Value) extends BasicHttpRequest

  case class ResultsResponse(url: String, view: Option[String] = None, body: Option[ResponseBody] = None,
    error: Option[String] = None) extends BasicHttpResponse

  case class ResultsParams(size: Option[Int] = None, loc: Option[String] = None)

  case class ShardBody(count: Int = 0, results: List[String]) extends ResponseBody

  trait ResultsProtocols extends DefaultJsonProtocol {
    import spray.json._
    implicit val viewFormat = jsonFormat7(NbaResultView)

    implicit object ResultsResponseWriter extends JsonWriter[ResultsResponse] {
      override def write(obj: ResultsResponse): spray.json.JsValue = {
        val url = JsString(obj.url.toString)
        val v = obj.view.fold(JsString("none")) { view ⇒ JsString(view) }
        val e = obj.error.fold(JsString("none")) { error ⇒ JsString(error) }
        obj.body match {
          case Some(ResultsByDateBody(c, list)) ⇒
            JsObject("url" -> url, "view" -> v, "body" -> JsObject("count" -> JsNumber(c),
              "results" -> JsArray(list.toList.map(r ⇒ r.toJson))), "error" -> e)
          case Some(ResultsByTeamBody(c, list)) =>
            JsObject("url" -> url, "view" -> v, "body" -> JsObject("count" -> JsNumber(c),
              "results" -> JsArray(list.map(r ⇒ r.toJson))), "error" -> e)
          case Some(ShardBody(c, addresses)) =>
            JsObject("url" -> url, "view" -> v, "body" -> JsObject("count" -> JsNumber(c),
              "results" -> JsArray(addresses.toJson)), "error" -> e)
          case None => JsObject("url" -> url, "view" -> v, "error" -> e)
        }
      }
    }
  }

  def errorLocation(value: String): String = s"'$value' is not a valid value for 'loc' parameter. We have support only for [home, away]"

  def errorSizeP(value: Int): String = s"$value is not a valid for 'size' parameter. It's should be positive value"

  val validationMessage = "empty"

  val defaultSize = 5
  val failbackWithDefaultLocation = { (error: String) ⇒ if (validationMessage == error) \/-(Location.All) else -\/(error) }
  val failbackWithDefaultSize = { (error: String) ⇒ if (validationMessage == error) \/-(defaultSize) else -\/(error) }

  val dtViewName = "results-by-date-view"
  val teamViewName = "last-results-by-team-view"

  //this props could be changed in runtime through query-side-results-archaius.properties
  private val lastResultsProps = "hystrix.api.lastResults.injectable.latency"
  private val resultsByDateProps = "hystrix.api.resultsByDate.injectable.latency"
  private val resultsByDateLatency = DynamicPropertyFactory.getInstance().getLongProperty(resultsByDateProps, 0)
  private val lastResultsLatency = DynamicPropertyFactory.getInstance().getLongProperty(lastResultsProps, 0)
}

trait ResultsMicroservice extends ShardedDomainReadService with Directives
    with ResultsProtocols
    with SystemSettings
    with AskSupport {
  mixin: MicroserviceKernel with DiscoveryClientSupport with DomainSupport ⇒
  import ResultsMicroservice._

  private val formatter = searchFormatter()

  override val name = "ResultsMicroservice"

  override val servicePathPostfix = "results"

  implicit override val timeout = akka.util.Timeout(settings.timeouts.results.getSeconds, TimeUnit.SECONDS)

  override lazy val endpoints =
    s"$httpPrefixAddress/$pathPref/$servicePathPostfix/{dt}" ::
      s"$httpPrefixAddress/$pathPref/$servicePathPostfix/{team}/last" :: Nil

  private val view = system.actorOf(ResultViewRouter.props(settings), "results-query")

  abstract override def configureApi() =
    super.configureApi() ~
      RestApiJunction(Option { ec: ExecutionContext ⇒ resultsRoute(ec) },
        Option { () ⇒
          ShardedDomain(system).start()
          system.log.info(s"\n★ ★ ★ ★ ★ ★ [$name] was started on $httpPrefixAddress ★ ★ ★ ★ ★ ★")
        },
        Option(() ⇒ system.log.info(s"\n★ ★ ★ ★ ★ ★ [$name] was stopped on $httpPrefixAddress ★ ★ ★ ★ ★ ★")))

  private def resultsRoute(implicit ec: ExecutionContext): Route = {
    pathPrefix(pathPref) {
      path(servicePathPostfix / Segment) { date ⇒
        get {
          withUri { uri ⇒
            requiredHttpSession(ex) { session ⇒
              complete {
                system.log.info(s"[$name]:[$session] - incoming request $uri")
                Thread.sleep(resultsByDateLatency.get) // for testing por
                //fail("fake error")
                Try {
                  formatter parse date
                } match {
                  case Success(dt)    ⇒ competeWithDate(uri, date)
                  case Failure(error) ⇒ fail(ResultsResponse(uri, error = Option(error.getMessage))).apply(error.getMessage)
                }
              }
            }
          }
        }
      } ~
        path(servicePathPostfix / Segment / "last") { team ⇒
          get {
            parameters('size.as[Int] ?, 'loc ?).as(ResultsParams) { params ⇒
              requiredHttpSession(ex) { session ⇒
                withUri { uri ⇒
                  complete {
                    import scalaz.Scalaz._
                    import scalaz._
                    system.log.info(s"[$name]:[$session] - incoming request $uri")
                    Thread.sleep(lastResultsLatency.get)
                    val complete = completeWithTeam(uri, team)
                    val loc = (for { l ← params.loc \/> (validationMessage) } yield l)
                      .flatMap(x ⇒ Location.values.find(_.toString == x) \/> (s"Wrong location $x"))
                      .fold(failbackWithDefaultLocation, { l ⇒ \/-(l) })

                    val size = (for { s ← params.size \/> (validationMessage) } yield s)
                      .flatMap { s ⇒ if (s >= 0) \/-(s) else -\/(s"Size $s should be positive") }
                      .fold(failbackWithDefaultSize, { l ⇒ \/-(l) })

                    (for { l ← loc; s ← size } yield (l, s))
                      .fold(error ⇒
                        fail(ResultsResponse(uri, view = Option(teamViewName), error = Option(error))).apply(error),
                        complete(_)
                      )
                  }
                }
              }
            }
          }
        }
    } ~
      path("showShardRegions") {
        get {
          withUri(uri ⇒ complete(completeShowRegions(uri)))
        }
      }
  }

  private def completeShowRegions(uri: String)(implicit ex: ExecutionContext): Future[HttpResponse] = {
    domain.ShardedDomain(system).showRegions
      .map { adds: CurrentRegions =>
        success(ResultsResponse(uri, view = Option("shard-regions"),
          body = Option(ShardBody(adds.regions.size, adds.regions.map(_.toString).toList))))
      }
      .recoverWith {
        case e: Throwable =>
          fail(ResultsResponse(uri, view = Option("shard-regions"), error = Option(e.getMessage))).apply(e.getMessage)
      }
  }

  private def completeWithTeam(uri: String, team: String)(implicit ex: ExecutionContext): ((Location.Value, Int)) ⇒ Future[HttpResponse] =
    tuple ⇒ {
      fetch[ResultsByTeamBody](GetResultsByTeam(uri, team, tuple._2, tuple._1), view) map {
        case \/-(res)   ⇒ success(ResultsResponse(uri, view = Option(teamViewName), body = Option(res)))
        case -\/(error) ⇒ fail(error)
      }
    }

  private def competeWithDate(uri: String, date: String)(implicit ex: ExecutionContext): Future[HttpResponse] =
    fetch[ResultsByDateBody](GetResultsByDate(uri, date), view) map {
      case \/-(res)   ⇒ success(ResultsResponse(uri, view = Option(dtViewName), body = Option(res)))
      case -\/(error) ⇒ fail(error)
    }
}
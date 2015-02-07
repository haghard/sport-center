package http

import akka.http.model.HttpResponse
import akka.http.server.{ Directives, Route }
import discovery.DiscoveryClientSupport
import domain.DomainSupport
import http.ResultsMicroservice._
import microservice.api.MicroserviceKernel
import microservice.crawler.{ Location, NbaResult }
import microservice.http.RestService.{ BasicHttpRequest, BasicHttpResponse, ResponseBody }
import microservice.http.RestWithDiscovery.DateFormatToJson
import microservice.http.{ RestApi, RestWithDiscovery }
import microservice.{ AskManagment, SystemSettings }
import query.DomainFinder
import query.DomainFinder.ResultsBody
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }
import scalaz.{ -\/, \/- }
import microservice.crawler.searchFormatter

object ResultsMicroservice {

  case class GetResultsByDate(url: String, dt: String) extends BasicHttpRequest
  case class GetResultsByTeam(url: String, name: String, size: Int, location: Location.Value) extends BasicHttpRequest

  case class ResultsResponse(url: String,
    view: Option[String] = None,
    body: Option[ResponseBody] = None,
    error: Option[String] = None) extends BasicHttpResponse

  case class ResultsParams(size: Option[Int] = None, loc: Option[String] = None)

  trait ResultsProtocols extends DefaultJsonProtocol {
    implicit val resultFormat = jsonFormat5(NbaResult.apply)

    implicit object ResultsResponseWriter extends JsonWriter[ResultsResponse] {
      import spray.json._
      override def write(obj: ResultsResponse): spray.json.JsValue = {
        val url = JsString(obj.url.toString)
        val v = obj.view.fold(JsString("none")) { view ⇒ JsString(view) }
        val e = obj.error.fold(JsString("none")) { error ⇒ JsString(error) }
        val body = obj.body match {
          case Some(ResultsBody(count, list)) ⇒
            JsObject("count" -> JsNumber(count), "results" -> JsArray(list.map(r ⇒ r.toJson)))
          case other ⇒ throw new SerializationException(s"Serialization error $other in ResultsMicroservice")
        }

        JsObject("url" -> url, "view" -> v, "body" -> body, "error" -> e)
      }
    }

  }

  def errorLocation(value: String): String = s"'$value' is not a valid value for 'loc' parameter. We have support only for [home, away]"

  def errorSizeP(value: Int): String = s"$value is not a valid for 'size' parameter. It's should be positive value"

  val validationMessage = "empty"

  val defaultSize = 5
  val failbackWithDefaultLocation = { (error: String) ⇒ if (validationMessage == error) \/-(Location.All) else -\/(error) }
  val failbackWithDefaultSize = { (error: String) ⇒ if (validationMessage == error) \/-(defaultSize) else -\/(error) }

  val dtVname = "results-by-date-view"
  val teamVname = "results-by-team-view"
}

trait ResultsMicroservice extends RestWithDiscovery
    with Directives with ResultsProtocols
    with SystemSettings
    with AskManagment {
  mixin: MicroserviceKernel with DiscoveryClientSupport with DomainSupport ⇒

  private val formatter = searchFormatter()

  override val name = "ResultsMicroservice"

  override val servicePathPostfix = "results"

  implicit override val timeout = akka.util.Timeout(2 seconds)

  override lazy val endpoints =
    List(
      s"$httpPrefixAddress/$pathPrefix/$servicePathPostfix/{dt}",
      s"$httpPrefixAddress/$pathPrefix/$servicePathPostfix/{team}/last")

  private lazy val finder = system.actorOf(DomainFinder.props(settings), name = "domain-finder")

  abstract override def configureApi() =
    super.configureApi() ~
      RestApi(route = Option { ec: ExecutionContext ⇒ resultsRoute(ec) },
        Option { () ⇒
          system.log.info(s"\n★ ★ ★  [$name] was started on $httpPrefixAddress ★ ★ ★")
        },
        Option { () ⇒
          system.log.info(s"\n★ ★ ★  [$name] was stopped on $httpPrefixAddress ★ ★ ★")
        }
      )

  private def resultsRoute(implicit ec: ExecutionContext): Route = {
    pathPrefix(pathPrefix) {
      (get & path(servicePathPostfix / Segment)) { date ⇒
        withUri { uri ⇒
          complete {
            system.log.info(s"[$name] - incoming request $uri")
            Try {
              formatter parse date
            } match {
              case Success(dt) ⇒ competeWithDate(uri, date)
              case Failure(error) ⇒
                fail(ResultsResponse(uri, error = Option(error.getMessage))).apply(error.getMessage)
            }
          }
        }
      } ~
        (get & path(servicePathPostfix / Segment / "last")) { team ⇒
          parameters('size.as[Int] ?, 'loc ?).as(ResultsParams) { params ⇒
            withUri { uri ⇒
              complete {
                import scalaz.Scalaz._
                import scalaz._

                system.log.info(s"[$name] - incoming request $uri")
                val complete = completeWithTeam(uri, team)
                val loc = (for { l ← params.loc \/> (validationMessage) } yield l)
                  .flatMap(x ⇒ Location.values.find(_.toString == x) \/> (s"Wrong location $x"))
                  .fold(failbackWithDefaultLocation, { l ⇒ \/-(l) })

                val size = (for { s ← params.size \/> (validationMessage) } yield s)
                  .flatMap { s ⇒ if (s >= 0) \/-(s) else -\/(s"Size $s should be positive") }
                  .fold(failbackWithDefaultSize, { l ⇒ \/-(l) })

                (for { l ← loc; s ← size } yield (l, s))
                  .fold(error ⇒
                    fail(ResultsResponse(uri, error = Option(error))).apply(error),
                    complete(_)
                  )
              }
            }
          }
        }
    }
  }

  private def completeWithTeam(uri: String, team: String)(implicit ex: ExecutionContext): ((Location.Value, Int)) ⇒ Future[HttpResponse] =
    tuple ⇒ {
      fetch[ResultsBody](GetResultsByTeam(uri, team, tuple._2, tuple._1), finder)
        .map {
          case \/-(res)   ⇒ success(ResultsResponse(uri, view = Option(teamVname), body = Option(res)))
          case -\/(error) ⇒ fail(error)
        }
    }

  private def competeWithDate(uri: String, date: String)(implicit ex: ExecutionContext): Future[HttpResponse] = {
    fetch[ResultsBody](GetResultsByDate(uri, date), finder)
      .map {
        case \/-(res)   ⇒ success(ResultsResponse(uri, view = Option(dtVname), body = Option(res)))
        case -\/(error) ⇒ fail(error)
      }
  }
}
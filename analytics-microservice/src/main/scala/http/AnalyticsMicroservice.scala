package http

import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.server._
import cassandra.SparkGuardian
import http.AnalyticsMicroservice.AnalyticsHttpProtocols
import microservice.http.RestService.BasicHttpResponse
import microservice.{ SystemSettings, AskManagment }

import spray.json._
import scalaz.{ -\/, \/- }
import microservice.api.MicroserviceKernel
import scala.concurrent.{ Future, ExecutionContext }
import microservice.http.{ RestApiJunction, RestWithDiscovery }
import cassandra.SparkJobManager.{ SparkJobView, Standing, SeasonStandingView, StandingBatchJobSubmit }

object AnalyticsMicroservice {
  case class SparkJobHttpResponse(url: String, view: Option[String] = None,
    body: Option[SparkJobView] = None, error: Option[String] = None) extends BasicHttpResponse

  trait AnalyticsHttpProtocols extends DefaultJsonProtocol {
    implicit val standingFormat = jsonFormat7(Standing.apply)

    implicit object ResultsResponseWriter extends JsonWriter[SparkJobHttpResponse] {
      import spray.json._
      override def write(obj: SparkJobHttpResponse): spray.json.JsValue = {
        val url = JsString(obj.url.toString)
        val v = obj.view.fold(JsString("none")) { view ⇒ JsString(view) }
        val e = obj.error.fold(JsString("none")) { error ⇒ JsString(error) }
        obj.body match {
          case Some(SeasonStandingView(c, west, east)) ⇒ JsObject(
            "western conference" -> JsArray(west.map(_.toJson)),
            "eastern conference" -> JsArray(east.map(_.toJson)),
            "url" -> url,
            "view" -> v,
            "body" -> JsObject("count" -> JsNumber(c)),
            "error" -> e)
          case None => JsObject("url" -> url, "view" -> v, "error" -> e)
        }
      }
    }
  }
}

trait AnalyticsMicroservice extends RestWithDiscovery
    with AnalyticsHttpProtocols with SystemSettings with AskManagment {
  mixin: MicroserviceKernel =>

  import AnalyticsMicroservice._
  import scala.concurrent.duration._

  override val name = "AnalyticsMicroservice"

  override val servicePathPostfix = "analytics"

  implicit override val timeout = akka.util.Timeout(15 seconds)

  private val mapReduceEngine = system.actorOf(SparkGuardian.props)

  override lazy val endpoints = List(s"$httpPrefixAddress/$servicePathPostfix")

  abstract override def configureApi() =
    super.configureApi() ~
      RestApiJunction(Option { ec: ExecutionContext ⇒ analyticsRoute(ec) },
        Option(() ⇒ system.log.info(s"\n★ ★ ★ ★ ★ ★ [$name] was started on $httpPrefixAddress ★ ★ ★ ★ ★ ★")),
        Option(() ⇒ system.log.info(s"\n★ ★ ★ ★ ★ ★ [$name] was stopped on $httpPrefixAddress ★ ★ ★ ★ ★ ★")))

  private def analyticsRoute(implicit ec: ExecutionContext): Route = {
    (get & path(pathPrefix / servicePathPostfix / Segment)) { stage =>
      withUri { uri ⇒
        get {
          complete(competeBatch(uri, stage))
        }
      }
    }
  }
  settings.teamConferences
  private def competeBatch(uri: String, stage: String)(implicit ex: ExecutionContext): Future[HttpResponse] = {
    val interval = (for { (k, v) <- settings.intervals if (v == stage) } yield (k)).headOption

    interval.fold(Future.successful(fail(s"Requested stage $stage not found"))) { i =>
      fetch[SparkJobView](StandingBatchJobSubmit(uri, stage, settings.teamConferences, i), mapReduceEngine) map {
        case \/-(res)   ⇒ success(SparkJobHttpResponse(uri, view = Option("standing-by-spark"), body = Option(res)))
        case -\/(error) ⇒ fail(error)
      }
    }
  }
}
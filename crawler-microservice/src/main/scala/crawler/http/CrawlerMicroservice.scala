package crawler.http

import akka.http.scaladsl.server.Route
import spray.json.JsonWriter
import crawler.{ NbaCampaignView, CrawlerGuardianSupport }
import NbaCampaignView.LastUpdateDate
import discovery.DiscoveryClientSupport
import microservice.AskSupport
import microservice.api.MicroserviceKernel
import microservice.http.ShardedDomainReadService.DateFormatToJson
import microservice.http.{ Session, RestApiJunction, ShardedDomainReadService }

import microservice.http.RestService.{ BasicHttpRequest, BasicHttpResponse, ResponseBody }

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scalaz.{ -\/, \/- }

object CrawlerMicroservice {

  /**
   * Request
   *
   */
  case class GetLastCrawlDate(url: String) extends BasicHttpRequest

  /*
   * Response
   */
  case class CrawlerHttpResponse(url: String, view: Option[String] = None,
    body: Option[ResponseBody] = None,
    error: Option[String] = None) extends BasicHttpResponse

  case class CrawlerResultsBody(last: LastUpdateDate, override val count: Int = 1) extends ResponseBody

  implicit object CampaignProtocols extends JsonWriter[CrawlerHttpResponse] {
    import spray.json._
    implicit val dtFormat = DateFormatToJson
    private val empty = JsObject("lastIterationDate" -> JsString("empty"))

    override def write(obj: CrawlerHttpResponse): JsValue = {
      obj.body match {
        case Some(CrawlerResultsBody(last, count)) ⇒
          last.lastIterationDate.fold(empty) { dt ⇒
            val body = JsObject("lastIterationDate" -> dt.toJson(dtFormat))
            JsObject("url" -> JsString(obj.url.toString),
              "view" -> obj.view.fold(JsString("none")) { view ⇒ JsString(view) },
              "body" -> body)
          }
        case None => throw new Exception("Empty body for CrawlerResponse")
      }
    }
  }

  private val viewName = "last-crawl-date"
}

trait CrawlerMicroservice extends ShardedDomainReadService with AskSupport {
  mixin: MicroserviceKernel with DiscoveryClientSupport with CrawlerGuardianSupport =>
  import _root_.crawler.http.CrawlerMicroservice._

  override val name = "CrawlerMicroservice"

  override val servicePathPostfix = "crawler"

  implicit override val timeout = akka.util.Timeout(2 seconds)

  override lazy val endpoints = List(s"$httpPrefixAddress/$pathPref/$servicePathPostfix")

  //private val view = system.actorOf(NbaCampaignView.props(httpDispatcher), name = "campaign-view")

  abstract override def configureApi() =
    super.configureApi() ~
      RestApiJunction(route = Option { ec: ExecutionContext ⇒ crawlerRoute(ec) },
        preAction = Option(() ⇒ system.log.info(s"\n★ ★ ★  [$name] was deployed $httpPrefixAddress ★ ★ ★")),
        postAction = Option(() ⇒ system.log.info(s"\n★ ★ ★  [$name] was stopped on $httpPrefixAddress ★ ★ ★")))

  private def crawlerRoute(implicit ec: ExecutionContext): Route = {
    pathPrefix(pathPref) {
      (get & path(servicePathPostfix)) {
        withUri { uri ⇒
          system.log.info(s"[$name] - incoming request $uri")
          complete {
            "Ok"
            /*fetch[LastUpdateDate](GetLastCrawlDate(uri), view) map {
              case \/-(resp)  ⇒ success(CrawlerHttpResponse(uri, view = Option(viewName), body = Option(CrawlerResultsBody(resp))))
              case -\/(error) ⇒ fail(error)
            }*/
          }
        }
      }
    }
  }
}
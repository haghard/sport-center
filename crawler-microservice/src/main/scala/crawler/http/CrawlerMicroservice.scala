package crawler.http

import akka.http.server._
import crawler.{ CampaignView, ChangeSetWriterSupport }
import CampaignView.LastUpdateDate
import crawler.ChangeSetWriterSupport
import discovery.DiscoveryClientSupport
import microservice.AskManagment
import microservice.api.MicroserviceKernel
import microservice.http.RestService.{ BasicHttpRequest, BasicHttpResponse, ResponseBody }
import microservice.http.RestWithDiscovery.DateFormatToJson
import microservice.http.{ RestApi, RestWithDiscovery }
import spray.json.JsonWriter

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
  case class CrawlerResponse(url: String, view: Option[String] = None,
    body: Option[ResponseBody] = None,
    error: Option[String] = None) extends BasicHttpResponse

  case class CrawlerResultsBody(last: LastUpdateDate, override val count: Int = 1) extends ResponseBody

  implicit object CampaignProtocols extends JsonWriter[CrawlerResponse] {
    import spray.json._
    implicit val dtFormat = DateFormatToJson
    private val empty = JsObject("lastIterationDate" -> JsString("empty"), "count" -> JsNumber(1))

    override def write(obj: CrawlerResponse): JsValue = {
      val url = JsString(obj.url.toString)
      val v = obj.view.fold(JsString("none")) { view ⇒ JsString(view) }
      val e = obj.error.fold(JsString("none")) { error ⇒ JsString(error) }
      val body = obj.body match {
        case Some(CrawlerResultsBody(last, count)) ⇒
          last.lastIterationDate.fold(empty) { dt ⇒
            JsObject("lastIterationDate" -> dt.toDate.toJson(dtFormat), "count" -> JsNumber(count))
          }
      }
      JsObject("url" -> url, "view" -> v, "body" -> body, "error" -> e)
    }
  }
}

trait CrawlerMicroservice extends RestWithDiscovery
    with AskManagment {
  mixin: MicroserviceKernel with DiscoveryClientSupport with ChangeSetWriterSupport ⇒

  import crawler.http.CrawlerMicroservice._

  override def name: String = "CrawlerMicroservice"

  override val servicePathPostfix = "crawler"

  implicit override val timeout = akka.util.Timeout(3 seconds)

  override lazy val endpoints = List(s"$httpPrefixAddress/$pathPrefix/$servicePathPostfix")

  private val view = system.actorOf(CampaignView.props(httpDispatcher), name = "campaign-view")

  /**
   *
   *
   * @return
   */
  abstract override def configureApi() =
    super.configureApi() ~
      RestApi(route = Option { ec: ExecutionContext ⇒ crawlerRoute(ec) },
        preAction = Option { () ⇒
          system.log.info(s"\n★ ★ ★  [$name] was started on $httpPrefixAddress ★ ★ ★")
        },
        postAction = Option { () ⇒
          system.log.info(s"\n★ ★ ★  [$name] was stopped on $httpPrefixAddress ★ ★ ★")
        }
      )

  private def crawlerRoute(implicit ec: ExecutionContext): Route = {
    pathPrefix(pathPrefix) {
      (get & path(servicePathPostfix)) {
        withUri { uri ⇒
          system.log.info(s"[$name] - incoming request $uri")
          complete {
            fetch[LastUpdateDate](GetLastCrawlDate(uri), view)
              .map {
                case \/-(resp)  ⇒ success(CrawlerResponse(url = uri, view = Option("last-crawl-date"), body = Some(CrawlerResultsBody(resp))))
                case -\/(error) ⇒ fail(error)
              }
          }
        }
      }
    }
  }
}
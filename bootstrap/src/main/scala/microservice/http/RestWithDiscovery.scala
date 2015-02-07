package microservice.http

import akka.http.model.HttpEntity.Strict
import akka.http.model.StatusCodes._
import akka.http.model.headers.Host
import akka.http.model.{ HttpResponse, MediaTypes }
import akka.http.server._
import akka.util.ByteString
import microservice.api.MicroserviceKernel
import microservice.api.MicroserviceKernel._
import microservice.http.RestService.BasicHttpResponse
import spray.json.{ JsonWriter, DefaultJsonProtocol, JsonFormat }

import scala.concurrent.Future
import spray.json._
import akka.http.model.Uri.{ Host => HostHeader }

object RestWithDiscovery {

  implicit object DateFormatToJson extends JsonFormat[java.util.Date] with DefaultJsonProtocol {
    import spray.json._
    val formatter = microservice.crawler.estFormatter()
    override def read(json: JsValue): java.util.Date = formatter.parse(json.convertTo[String])
    override def write(date: java.util.Date) = formatter.format(date).toJson
  }
}

trait RestWithDiscovery extends BootableRestService
    with Directives {

  self: MicroserviceKernel =>

  /**
   *
   *
   * @return
   */
  implicit def timeout: akka.util.Timeout

  /**
   *
   *
   */
  protected lazy val key = s"akka.tcp://${ActorSystemName}@${localAddress}:${akkaSystemPort}"

  /**
   *
   * @return
   */
  def withUri: Directive1[String] = extract(_.request.uri.toString())

  /**
   *
   * @return
   */
  def endpoints: List[String]

  /**
   *
   *
   * @return
   */
  def servicePathPostfix: String

  /**
   *
   * @param resp
   * @param writer
   * @tparam T
   * @return
   */
  protected def fail[T <: BasicHttpResponse](resp: T)(implicit writer: JsonWriter[T]): String => Future[HttpResponse] =
    error =>
      Future.successful(
        HttpResponse(
          BadRequest,
          List(Host(HostHeader(localAddress), httpPort)),
          Strict(MediaTypes.`application/json`, ByteString(resp.toJson.prettyPrint))))

  /**
   *
   * @param error
   * @return
   */
  protected def fail(error: String) =
    HttpResponse(
      InternalServerError,
      List(Host(HostHeader(localAddress), httpPort)), error)

  /**
   *
   * @param resp
   * @param writer
   * @tparam T
   * @return
   */
  protected def success[T <: BasicHttpResponse](resp: T)(implicit writer: JsonWriter[T]) =
    HttpResponse(
      OK,
      List(Host(HostHeader(localAddress), httpPort)),
      Strict(MediaTypes.`application/json`, ByteString(resp.toJson.prettyPrint)))
}
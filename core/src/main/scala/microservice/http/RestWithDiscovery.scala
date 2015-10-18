package microservice.http

import akka.http.scaladsl.model.HttpEntity.Strict
import akka.http.scaladsl.model.headers.Host
import akka.http.scaladsl.model.{ MediaTypes, HttpResponse }
import akka.http.scaladsl.server.Directive1
import akka.util.ByteString
import microservice.api.MicroserviceKernel
import microservice.api.MicroserviceKernel._
import microservice.http.RestService.BasicHttpResponse
import spray.json.{ JsonWriter, DefaultJsonProtocol, JsonFormat }

import scala.concurrent.Future
import spray.json._
import akka.http.scaladsl.model.Uri.{ Host => HostHeader }

object RestWithDiscovery {
  implicit object DateFormatToJson extends JsonFormat[java.util.Date] with DefaultJsonProtocol {
    import spray.json._
    val formatter = microservice.crawler.estFormatter()
    override def read(json: JsValue): java.util.Date = formatter.parse(json.convertTo[String])
    override def write(date: java.util.Date) = formatter.format(date).toJson
  }
}

trait RestWithDiscovery extends BootableRestService {
  self: MicroserviceKernel =>

  implicit def timeout: akka.util.Timeout

  def endpoints: List[String]

  def servicePathPostfix: String

  lazy val key = s"akka.tcp://$ActorSystemName@$localAddress:$akkaSystemPort"

  def withUri: Directive1[String] = extract(_.request.uri.toString())

  protected def fail[T <: BasicHttpResponse](resp: T)(implicit writer: JsonWriter[T]): String => Future[HttpResponse] =
    error =>
      Future.successful(
        HttpResponse(
          akka.http.scaladsl.model.StatusCodes.BadRequest,
          List(Host(HostHeader(localAddress), httpPort)),
          Strict(MediaTypes.`application/json`, ByteString(resp.toJson.prettyPrint))))

  protected def fail(error: String) =
    HttpResponse(akka.http.scaladsl.model.StatusCodes.InternalServerError, List(Host(HostHeader(localAddress), httpPort)), error)

  protected def success[T <: BasicHttpResponse](resp: T)(implicit writer: JsonWriter[T]) =
    HttpResponse(
      akka.http.scaladsl.model.StatusCodes.OK,
      List(Host(HostHeader(localAddress), httpPort)),
      Strict(MediaTypes.`application/json`, ByteString(resp.toJson.prettyPrint)))
}
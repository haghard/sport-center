package microservice.http

import akka.http.scaladsl.model.HttpEntity.Strict
import akka.http.scaladsl.model.headers.{OAuth2BearerToken, Host, Authorization, Location}
import akka.http.scaladsl.model.{HttpMethods, MediaTypes, HttpResponse}
import akka.util.ByteString
import microservice.api.MicroserviceKernel
import microservice.api.MicroserviceKernel._
import microservice.http.RestService.BasicHttpResponse
import spray.json.{ JsonWriter, DefaultJsonProtocol, JsonFormat }

import scala.concurrent.Future
import spray.json._
import akka.http.scaladsl.model.Uri.{ Host => HostHeader }

object ShardedDomainReadService {
  implicit object DateFormatToJson extends JsonFormat[java.util.Date] with DefaultJsonProtocol {
    import spray.json._
    val formatter = microservice.crawler.estFormatter()
    override def read(json: JsValue): java.util.Date = formatter.parse(json.convertTo[String])
    override def write(date: java.util.Date) = formatter.format(date).toJson
  }
}

trait ShardedDomainReadService extends BootableRestService {
  self: MicroserviceKernel =>

  implicit def timeout: akka.util.Timeout

  def endpoints: List[String]

  def servicePathPostfix: String

  lazy val key = s"akka.tcp://$ActorSystemName@$externalAddress:$akkaSystemPort"

  def fail[T <: BasicHttpResponse](resp: T)(implicit writer: JsonWriter[T]): String => Future[HttpResponse] =
    error =>
      Future.successful(
        HttpResponse(akka.http.scaladsl.model.StatusCodes.BadRequest,
          List(Host(HostHeader(localAddress), httpPort)),
          Strict(MediaTypes.`application/json`, ByteString(resp.toJson.prettyPrint))))

  def fail(error: String) =
    HttpResponse(akka.http.scaladsl.model.StatusCodes.InternalServerError, List(Host(HostHeader(localAddress), httpPort)), error)

  //Expires
  def success[T <: BasicHttpResponse](resp: T, token: String)(implicit writer: JsonWriter[T]) =
    HttpResponse(akka.http.scaladsl.model.StatusCodes.OK,
      List(
        Host(HostHeader(localAddress), httpPort),
        Location(s"http://$localAddress:$httpPort/$pathPref/$servicePathPostfix"),
        Authorization(OAuth2BearerToken(token))
      ),
      Strict(MediaTypes.`application/json`, ByteString(resp.toJson.prettyPrint)))

  def success[T <: BasicHttpResponse](resp: T)(implicit writer: JsonWriter[T]) =
      HttpResponse(akka.http.scaladsl.model.StatusCodes.OK,
        List(Host(HostHeader(localAddress), httpPort), Location(s"http://$localAddress:$httpPort/$pathPref/$servicePathPostfix")),
        Strict(MediaTypes.`application/json`, ByteString(resp.toJson.prettyPrint)))
}
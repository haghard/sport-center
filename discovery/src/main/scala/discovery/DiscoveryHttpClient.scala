package discovery

import java.io.IOException

import akka.http.Http
import akka.http.model._
import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.{ FlowMaterializer, MaterializerSettings }
import akka.util.ByteString
import discovery.DiscoveryHttpClient.Protocols
import microservice.http.{ RestWithDiscovery, SprayJsonMarshalling }
import spray.json.DefaultJsonProtocol

import scala.concurrent.Future
import scala.concurrent.forkjoin.ThreadLocalRandom
import scalaz.{ -\/, \/- }

object DiscoveryHttpClient {

  trait Protocols extends DefaultJsonProtocol {

    case class RequestJson(key: String, value: String)

    object RequestJson extends DefaultJsonProtocol {
      implicit val jsonFormat = jsonFormat2(RequestJson.apply)
    }
  }
}

trait DiscoveryHttpClient extends DiscoveryClient
    with Protocols
    with SprayJsonMarshalling {
  mixin: RestWithDiscovery with DiscoveryClientSupport ⇒

  import akka.http.model.HttpMethods._
  import akka.http.model.MediaTypes._

  implicit val materializer = FlowMaterializer(MaterializerSettings(system)
    .withDispatcher(discoveryDispatcherName))(system)

  implicit val discoveryClientEc = system.dispatchers.lookup(discoveryDispatcherName)

  val Path = "/discovery"
  /**
   *
   * @param k
   * @param v
   * @return
   */
  override def set(k: String, v: String): Future[StatusCode] = {
    val data = ByteString(RequestJson(k, v).toJson.prettyPrint)
    val req = HttpRequest(
      POST,
      uri = Path,
      entity = HttpEntity(`application/json`, data))
    call(req)
  }

  /**
   *
   * @param k
   * @return
   */
  override def delete(k: String): Future[StatusCode] = ???

  /**
   *
   * @param k
   * @param v
   * @return
   */
  override def delete(k: String, v: String): Future[StatusCode] = {
    val data = ByteString(RequestJson(k, v).toJson.prettyPrint)
    val req = HttpRequest(
      PUT,
      uri = Path,
      entity = HttpEntity(`application/json`, data))
    call(req)
  }

  import akka.http.model.StatusCodes._
  private def call(req: HttpRequest): Future[StatusCode] = {
    askForDiscoveryNodeAddresses()
      .flatMap {
        case \/-(nodes) ⇒
          val size = nodes.size
          val address = nodes(ThreadLocalRandom.current().nextInt(size) % size)
          (for {
            host ← address.host
            port ← address.port
          } yield {
            system.log.info("Discovery node {} was selected for registration", address)
            Http(system).outgoingConnection(host, port)
          }).fold(Future.failed[StatusCode](new IOException(s"Empty host or port $address"))) { con ⇒
            Source.single(req)
              .via(con.flow)
              .runWith(Sink.head)
              .flatMap { response ⇒
                response.status match {
                  case OK    ⇒ Future.successful(OK)
                  case other ⇒ Future.failed(new IOException(other.toString))
                }
              }
          }
        case -\/(error) ⇒ Future.failed(new IOException(error))
      }
  }
}
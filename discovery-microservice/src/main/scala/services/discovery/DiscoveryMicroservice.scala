package services.discovery

import akka.actor.ActorRef
import akka.contrib.datareplication.LWWMap
import akka.http.marshalling._
import akka.http.model.{ HttpCharsets, HttpResponse, StatusCodes }
import akka.http.server.Directives
import akka.stream.actor.ActorPublisher
import akka.stream.scaladsl.Source
import akka.stream.{ FlowMaterializer, MaterializerSettings }
import discovery.ServiceDiscovery
import discovery.ServiceDiscovery._
import microservice.api.{ BootableMicroservice, ClusterNetworkSupport }
import microservice.http.{ BootableRestService, RestApi, SprayJsonMarshalling }
import spray.json.{ DefaultJsonProtocol, _ }

import scala.concurrent.ExecutionContext
import scalaz.{ -\/, \/- }

object DiscoveryMicroservice {

  case class KVRequest(key: String, value: String)

  object KVRequest extends DefaultJsonProtocol {
    implicit val jsonFormat = jsonFormat2(this.apply)
  }

  val routes = "routes"
  val scalarResponce = "scalar"
  val streamResponse = "stream"
  val servicePrefix = "discovery"

  val encoding = "utf-8"

  type ToMessage[A] = A ⇒ SSEvents.Message

  def messageToResponseMarshaller[A: ToMessage](implicit ec: ExecutionContext): ToResponseMarshaller[Source[A]] =
    Marshaller.withFixedCharset(SSEvents.`text/event-stream`.mediaType, HttpCharsets.`UTF-8`) { srcMes ⇒
      import akka.http.model.HttpEntity._
      val fullEntity = CloseDelimited(SSEvents.`text/event-stream`.mediaType, srcMes.map(_.toByteString))
      HttpResponse(entity = fullEntity)
    }

  implicit def mapToMessage(replica: LWWMap) =
    SSEvents.Message(replica.entries.asInstanceOf[Map[String, DiscoveryLine]].values.toList.toJson.prettyPrint)
}

/**
 * curl http://192.168.0.143:9001/discovery/scalar
 * curl http://192.168.0.143:9001/discovery/stream
 *
 * curl -i -X DELETE http://192.168.0.143:9005/discovery/api.results
 * curl -i -X POST -d '{"key":"api.results","value":"6876"}' -H "Content-Type:application/json" http://192.168.0.143:9001/discovery
 * curl -i -X PUT -d '{"key":"api.results","value":"6876"}' -H "Content-Type:application/json" http://192.168.0.143:9015/discovery
 */
trait DiscoveryMicroservice extends BootableRestService
    with Directives
    with SprayJsonMarshalling
    with DefaultJsonProtocol {
  mixin: ClusterNetworkSupport with BootableMicroservice ⇒
  import services.discovery.DiscoveryMicroservice._

  implicit val materializer = FlowMaterializer(MaterializerSettings(system)
    .withDispatcher(httpDispatcher))(system)

  abstract override def configureApi() =
    super.configureApi() ~
      RestApi(route = Some { ec: ExecutionContext ⇒ discoveryRoute(ec) })

  private def streamPublisher(): ActorRef =
    system.actorOf(ServiceRegistryPublisher.props(httpDispatcher))

  private def curl(method: String, resourcePath: String) =
    s"curl -i -X $method http://$localAddress:$httpPort/$resourcePath"

  private def discoveryRoute(implicit ec: ExecutionContext) =
    path(routes) {
      get { ctx ⇒
        ctx.complete(StatusCodes.OK, List(
          curl("GET", s"$servicePrefix/$scalarResponce"),
          curl("GET", s"$servicePrefix/$streamResponse"),
          curl("""POST -d '{"key":"api.results","value":"111"}' -H "Content-Type:application/json" """, servicePrefix),
          curl("""PUT -d '{"key":"api.results","value":"111"}' -H "Content-Type:application/json" """, servicePrefix),
          curl("DELETE", s"$servicePrefix/api.results")
        ))
      }
    } ~ pathPrefix(servicePrefix) {
      path(streamResponse) {
        get {
          complete {
            //experimental staff
            ToResponseMarshallable(Source(ActorPublisher[LWWMap](streamPublisher())))(messageToResponseMarshaller)
          }
        }
      } ~ path(scalarResponce) {
        get { ctx ⇒
          ServiceDiscovery(system)
            .findAll
            .flatMap {
              case \/-(r)     ⇒ ctx.complete((r.items).toMap.toJson.prettyPrint)
              case -\/(error) ⇒ ctx.complete(StatusCodes.NotFound, error)
            }
        }
      }
    } ~ path(servicePrefix) {
      post {
        entity(as[KVRequest]) { kv ⇒
          complete {
            system.log.info("Attempt to install {}", kv.toJson.prettyPrint)
            ServiceDiscovery(system)
              .setKey(SetKey(KV(kv.key, kv.value)))
              .map {
                case \/-(r) ⇒
                  val message = s"Service kv ${kv.toJson.prettyPrint} was registered"
                  system.log.info("{}", message)
                  HttpResponse(StatusCodes.OK, entity = message)
                case -\/(error) ⇒
                  system.log.info("{}", error)
                  HttpResponse(StatusCodes.InternalServerError, entity = error)
              }
          }
        }
      } ~ put {
        entity(as[KVRequest]) { kv ⇒
          complete {
            ServiceDiscovery(system)
              .unsetKey(UnsetKey(KV(kv.key, kv.value)))
              .map {
                case \/-(r)     ⇒ HttpResponse(StatusCodes.OK, entity = s"Service kv ${kv.toJson.prettyPrint} was unregistered")
                case -\/(error) ⇒ HttpResponse(StatusCodes.InternalServerError)
              }
          }
        }
      }
    } ~ path(servicePrefix / Segment) { key ⇒
      delete {
        complete {
          ServiceDiscovery(system)
            .deleteAll(UnsetAddress(key))
            .map {
              case \/-(r)     ⇒ HttpResponse(StatusCodes.OK, entity = s"Service $key was unregistered")
              case -\/(error) ⇒ HttpResponse(StatusCodes.InternalServerError, entity = error)
            }
        }
      }
    }
}
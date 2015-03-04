package services.discovery

import akka.http.marshalling._
import akka.stream.scaladsl.Source
import akka.stream.actor.ActorPublisher
import akka.contrib.datareplication.LWWMap
import akka.http.server.{ Route, Directives }
import akka.http.model.HttpResponse
import akka.stream.{ ActorFlowMaterializerSettings, ActorFlowMaterializer }
import discovery.ServiceDiscovery
import discovery.ServiceDiscovery._
import microservice.api.{ BootableMicroservice, ClusterNetworkSupport }
import microservice.http.{ BootableRestService, RestApi, SprayJsonMarshalling }
import spray.json.{ DefaultJsonProtocol, _ }
import akka.http.model.StatusCodes._

import scala.concurrent.ExecutionContext
import scalaz.{ -\/, \/- }

object DiscoveryMicroservice {
  case class KVRequest(key: String, value: String)

  object KVRequest extends DefaultJsonProtocol {
    implicit val jsonFormat = jsonFormat2(this.apply)
  }

  val scalarResponce = "scalar"
  val streamResponse = "stream"
  val servicePrefix = "discovery"
}

trait DiscoveryMicroservice extends BootableRestService
    with Directives
    with SprayJsonMarshalling
    with DefaultJsonProtocol {
  mixin: ClusterNetworkSupport with BootableMicroservice ⇒
  import services._
  import services.discovery.DiscoveryMicroservice._

  implicit val materializer = ActorFlowMaterializer(
    ActorFlowMaterializerSettings(system)
      .withDispatcher(httpDispatcher))(system)

  abstract override def configureApi() =
    super.configureApi() ~
      RestApi(route = Option { ec: ExecutionContext ⇒ discoveryRoute(ec) })

  private def streamPublisher() = system.actorOf(ServiceRegistryPublisher.props(httpDispatcher))

  private def discoveryRoute(implicit ec: ExecutionContext): Route =
    pathPrefix(servicePrefix) {
      path(streamResponse) {
        get {
          complete {
            ToResponseMarshallable(Source(ActorPublisher[LWWMap](streamPublisher())))(messageToResponseMarshaller)
          }
        }
      } ~ path(scalarResponce) {
        get { ctx ⇒
          ServiceDiscovery(system)
            .findAll
            .flatMap {
              case \/-(r)     ⇒ ctx.complete((r.items).toMap.toJson.prettyPrint)
              case -\/(error) ⇒ ctx.complete(NotFound, error)
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
                  HttpResponse(OK, entity = message)
                case -\/(error) ⇒
                  system.log.info("{}", error)
                  HttpResponse(InternalServerError, entity = error)
              }
          }
        }
      } ~ put {
        entity(as[KVRequest]) { kv ⇒
          complete {
            ServiceDiscovery(system)
              .unsetKey(UnsetKey(KV(kv.key, kv.value)))
              .map {
                case \/-(r)     ⇒ HttpResponse(OK, entity = s"Service kv ${kv.toJson.prettyPrint} was unregistered")
                case -\/(error) ⇒ HttpResponse(InternalServerError)
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
              case \/-(r)     ⇒ HttpResponse(OK, entity = s"Service $key was unregistered")
              case -\/(error) ⇒ HttpResponse(InternalServerError, entity = error)
            }
        }
      }
    }
}
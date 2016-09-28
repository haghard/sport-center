package http

import akka.cluster.ddata.LWWMap
import akka.http.scaladsl.model.HttpEntity.Strict
import akka.http.scaladsl.server.{ Route, Directives }
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import akka.stream.actor.ActorPublisher
import akka.stream.{ Materializer, ActorMaterializerSettings, ActorMaterializer }
import akka.stream.scaladsl.Source
import discovery.ServiceDiscovery
import discovery.ServiceDiscovery._
import microservice.SystemSettings
import microservice.api.{ MicroserviceKernel, ClusterNetworkSupport, BootableMicroservice }
import microservice.http.RestApiJunction
import discovery.ServiceRegistryPublisher
import spray.json.DefaultJsonProtocol
import akka.http.scaladsl.model.{ HttpResponse, HttpRequest }
import akka.http.scaladsl.model.StatusCodes._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try
import spray.json._

import scalaz.{ -\/, \/- }

object DiscoveryMicroservice {
  case class KVRequest(key: String, value: String)

  val scalarResponse = "scalar"
  val streamResponse = "stream"
  val servicePrefix = "discovery"

  trait DiscoveryProtocols extends DefaultJsonProtocol {
    implicit def materializer: Materializer

    implicit val kvFormat = jsonFormat2(KVRequest.apply)
    implicit def unmarshaller(implicit ec: ExecutionContext) = new FromRequestUnmarshaller[KVRequest]() {
      override def apply(req: HttpRequest)(implicit ec: ExecutionContext, mat: Materializer): Future[KVRequest] =
        Try(Future(req.entity.asInstanceOf[Strict].data.decodeString("UTF-8").parseJson.convertTo[KVRequest]))
          .getOrElse(Future.failed(new Exception("Can't parse KVRequest")))
    }
  }
}

trait DiscoveryMicroservice extends UsersMicroservices
    with Directives
    with DiscoveryMicroservice.DiscoveryProtocols
    with SSEventsMarshalling
    with DefaultJsonProtocol
    with SystemSettings {
  mixin: ClusterNetworkSupport with BootableMicroservice ⇒
  import DiscoveryMicroservice._

  implicit val materializer = ActorMaterializer(
    ActorMaterializerSettings(system)
      .withDispatcher(MicroserviceKernel.microserviceDispatcher)
  )(system)

  abstract override def configureApi() =
    super.configureApi() ~ RestApiJunction(
      route = Option({ ec: ExecutionContext ⇒ discoveryRoute(ec) }),
      preAction = Option { () =>
        system.log.info(s"\n★ ★ ★ Discovery: [$httpPrefixAddress/$servicePrefix/$streamResponse] [$httpPrefixAddress/$servicePrefix/$scalarResponse] ★ ★ ★")
      }
    )

  private def streamPublisher() = system.actorOf(ServiceRegistryPublisher.props(httpDispatcher))

  implicit val DiscoveryMarshaller = messageToResponseMarshaller[LWWMap[DiscoveryLine], akka.NotUsed]

  private def discoveryRoute(implicit ec: ExecutionContext): Route =
    pathPrefix(servicePrefix) {
      path(streamResponse) {
        get {
          complete {
            //: ToResponseMarshallable
            Source.fromPublisher(ActorPublisher[LWWMap[DiscoveryLine]](streamPublisher()))
          }
        }
      } ~ path(scalarResponse) {
        get { ctx ⇒
          ServiceDiscovery(system)
            .findAll
            .flatMap {
              case \/-(r) ⇒ ctx.complete((r.items).toMap.toJson.prettyPrint)
              case -\/(error) ⇒ ctx.complete(NotFound, error)
            }
        }
      }
    } ~ path(servicePrefix) {
      post {
        entity(as[KVRequest]) { kv ⇒
          complete {
            system.log.info("Attempt to install {}", kv.toJson.prettyPrint)
            ServiceDiscovery(system).setKey(SetKey(KV(kv.key, kv.value)))
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
                case \/-(r) ⇒ HttpResponse(OK, entity = s"Service kv ${kv.toJson.prettyPrint} was unregistered")
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
              case \/-(r) ⇒ HttpResponse(OK, entity = s"Service $key was unregistered")
              case -\/(error) ⇒ HttpResponse(InternalServerError, entity = error)
            }
        }
      }
    }
}
package microservice.http

import akka.http.scaladsl.Http
import spray.json.DefaultJsonProtocol
import microservice.api.MicroserviceKernel
import akka.actor.{ Actor, ActorLogging, Props }
import akka.http.scaladsl.server.{ Directives, Route }
import akka.stream.{ ActorMaterializerSettings, ActorMaterializer }

import scala.concurrent.ExecutionContext

object RestService {

  trait BasicHttpRequest {
    def url: String
  }

  trait BasicHttpResponse {
    def url: String
    def view: Option[String]
    def error: Option[String]
    def body: Option[ResponseBody]
  }

  trait ResponseBody {
    def count: Int
  }

  def props(route: Route, interface: String, port: Int)(implicit ex: ExecutionContext) =
    Props(new RestService(route, interface, port))
}

final class RestService private (route: Route, interface: String, port: Int)(implicit ex: ExecutionContext)
    extends Actor with ActorLogging
    with DefaultJsonProtocol
    with Directives {

  implicit val materializer = ActorMaterializer(
    ActorMaterializerSettings(context.system)
      .withDispatcher(MicroserviceKernel.microserviceDispatcher))(context.system)

  Http()(context.system)
    .bindAndHandle(route, interface, port)

  override def receive: Receive = Actor.emptyBehavior
}
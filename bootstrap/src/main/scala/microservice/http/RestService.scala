package microservice.http

import akka.actor.{ Actor, ActorLogging, Props }
import akka.http.Http
import akka.http.server.{ Directives, Route }
import akka.stream.{ FlowMaterializer, MaterializerSettings }
import microservice.api.MicroserviceKernel
import spray.json.DefaultJsonProtocol

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

class RestService private (route: Route, interface: String, port: Int)(implicit ex: ExecutionContext)
    extends Actor with ActorLogging
    with DefaultJsonProtocol
    with Directives {

  implicit val materializer =
    FlowMaterializer(MaterializerSettings(context.system)
      .withDispatcher(MicroserviceKernel.microserviceDispatcher))(context.system)

  Http()(context.system)
    .bind(interface, port)
    .startHandlingWith(route)

  override def receive: Receive = Actor.emptyBehavior
}
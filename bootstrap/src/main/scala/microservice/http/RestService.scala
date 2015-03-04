package microservice.http

import akka.http.Http
import akka.http.model.HttpEntity.Strict
import akka.http.model.StatusCodes._
import akka.http.model.Uri.Host
import akka.http.model.headers.Host
import akka.http.model.headers.Host
import akka.http.model.{ MediaTypes, HttpResponse, HttpRequest }
import akka.stream.actor.ActorSubscriber
import akka.util.ByteString
import spray.json.DefaultJsonProtocol
import microservice.api.MicroserviceKernel
import akka.http.server.{ RouteResult, Directives, Route }
import akka.actor.{ ActorRef, Actor, ActorLogging, Props }
import akka.stream.{ ActorFlowMaterializerSettings, ActorFlowMaterializer }

import scala.concurrent.{ Future, ExecutionContext }

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

  import scala.concurrent.duration._
  import akka.stream.scaladsl._
  import akka.stream.scaladsl.{ Source, UndefinedSink, UndefinedSource, Flow }

  implicit val materializer = ActorFlowMaterializer(
    ActorFlowMaterializerSettings(context.system)
      .withDispatcher(MicroserviceKernel.microserviceDispatcher))(context.system)

  //val v = limitGlobal(context.system.actorOf(Limiter.props(1, 5 seconds, 1)))

  Http()(context.system).bind(interface, port)
    .startHandlingWith(route)

  override def receive: Receive = Actor.emptyBehavior

  /*def rateLimiter(): Flow[HttpRequest, HttpResponse] = {
    val in = UndefinedSource[HttpRequest]
    val out = UndefinedSink[HttpResponse]
    val tk = TickSource(0.seconds, 3 seconds, () => System.currentTimeMillis())
    /*(1000 / elemsPerSec).millis*/

    //val zipper = ZipWith[HttpRequest, () => Long] {}

    val zipper = ZipWith[() => Long, HttpRequest, HttpResponse] { (ts: () => Long, t: HttpRequest) =>
      val time = ts()
      log.info(s"Pas next req ${time} - ${this.hashCode()}")
      HttpResponse(OK, entity = Strict(MediaTypes.`application/json`, ByteString(time.toString)))
    }

    Flow() { implicit b =>
      import FlowGraphImplicits._
      in ~> zipper.right
      tk ~> zipper.left
      zipper.out ~> out

      /*zipper.out ~> Flow[(() => Long, HttpRequest)].map { x =>
        HttpResponse(OK, entity = Strict(MediaTypes.`application/json`, ByteString(x._1().toString)))
      } ~> out
      Flow[HttpRequest].map { x =>
        log.info(s"Pas next req ${System.currentTimeMillis} - ${this.hashCode()}")
        x
      } ~> out
      */
      (in, out)
    }
  }*/

  def limitGlobal(limiter: ActorRef): Flow[HttpRequest, HttpResponse] = {
    import akka.pattern.ask
    import akka.util.Timeout

    val maxAllowedWait: FiniteDuration = 7 seconds
    val f1 = Flow[HttpRequest].mapAsync { implicit b =>
      limiter.ask(Limiter.Acquire)(Timeout(maxAllowedWait))
        .map(_ => HttpResponse(OK, entity = Strict(MediaTypes.`application/json`, ByteString(System.currentTimeMillis().toString))))
    }

    Flow() { implicit b =>
      import FlowGraphImplicits._
      val in = UndefinedSource[HttpRequest]
      val out = UndefinedSink[HttpResponse]
      in ~> f1 ~> out
      (in, out)
    }
  }
}
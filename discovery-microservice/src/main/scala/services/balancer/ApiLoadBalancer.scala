package services.balancer

import java.util.concurrent.ThreadLocalRandom

import akka.actor._
import akka.contrib.datareplication.LWWMap
import akka.contrib.datareplication.Replicator.Changed
import akka.http.Http
import akka.http.model.HttpMethods._
import akka.http.model.headers.Host
import akka.http.model.{ HttpEntity, HttpRequest, HttpResponse, StatusCodes }
import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.{ FlowMaterializer, MaterializerSettings }
import discovery.ServiceDiscovery.DiscoveryLine
import akka.http.model.StatusCodes._

import scala.util.{ Failure, Success }

object ApiLoadBalancer {
  //TODO: add microservice version in url 
  val fragmentExp = """http://(\d{0,3}.\d{0,3}.\d{0,3}.\d{0,3}.):(\d{4,})([\d|/|\w|\{|\}]+)""".r

  case class Route(host: String, port: Int, pathRegex: String)

  def props(dispatcher: String, localAddress: String, httpPort: Int) =
    Props(new ApiLoadBalancer(dispatcher, localAddress, httpPort)).withDispatcher(dispatcher)
}

class ApiLoadBalancer private (dispatcher: String, localAddress: String, httpPort: Int) extends Actor with ActorLogging {
  import services.balancer.ApiLoadBalancer._

  implicit val materializer = FlowMaterializer(MaterializerSettings(context.system)
    .withDispatcher(dispatcher))(context.system)

  implicit val ex = context.system.dispatchers.lookup(dispatcher)

  private var routees: Option[Map[String, List[Route]]] = None

  override def preRestart(reason: scala.Throwable, message: scala.Option[scala.Any]): scala.Unit = {
    log.info("Balancer was restarted and lost all routees {}", reason.getMessage)
  }

  private def updateRoutees(map: LWWMap) = {
    map.entries.values.toList.asInstanceOf[List[DiscoveryLine]].map(_.urls)
      .flatten
      .map {
        case fragmentExp(host, port, path) ⇒
          if (path.indexOf('{') == -1 & path.indexOf('}') == -1) {
            Route(host, port.toInt, path)
          } else {
            val param = path.substring(path.indexOf('{') + 1, path.indexOf('}'))
            Route(host, port.toInt, path.replaceAll("\\{" + param + "\\}", "(.*)"))
          }
        case other ⇒ Route("localhost", 2000, "/none")
      }.groupBy(_.pathRegex)
  }

  override def receive: Receive = {
    case Changed(key, replica) if replica.isInstanceOf[LWWMap] ⇒
      routees = Option(updateRoutees(replica.asInstanceOf[LWWMap]))
      log.info("Map {}", routees)

    case r: HttpRequest ⇒
      val replyTo = sender()
      log.info("Incoming request: {}", r.uri.toString)
      findRoute(r).fold {
        log.info("No routee for path {}", r.uri.path)
        replyTo ! HttpResponse(status = BadGateway, entity = HttpEntity(s"No routee for path ${r.uri.path}"))
      } { route: Route ⇒
        val reqUri = r.uri
        val redirectedUri = reqUri.withHost(route.host).withPort(route.port)
        log.info("Redirection {} -> {}", reqUri.toString(), redirectedUri.toString())
        val connection = Http(context.system).outgoingConnection(route.host, route.port)
        val req = HttpRequest(GET, uri = redirectedUri)
        Source.single(req)
          .via(connection.flow)
          .runWith(Sink.head)
          .onComplete {
            case Success(response) ⇒ replyTo ! response
            case Failure(error) ⇒
              replyTo ! HttpResponse(InternalServerError, List(Host(akka.http.model.Uri.Host(localAddress), port = httpPort)),
                error.getMessage)
          }
      }
  }

  private def findRoute(r: HttpRequest): Option[Route] = {
    routees.map { routes ⇒
      routes.keySet.find { route ⇒
        route.r.findFirstIn(r.uri.path.toString) match {
          case Some(_) ⇒ true
          case None    ⇒ false
        }
      }.flatMap(randomNext)
    }.flatten
  }

  //TODO: implement other routing strategy: rnr, ... etc
  private def randomNext(routePath: String): Option[Route] = {
    log.info(s"We've found $routePath")
    routees.get.get(routePath)
      .flatMap { routes ⇒
        if (routes.size > 0) {
          val size = routes.size
          val index = ThreadLocalRandom.current().nextInt(size)
          Option(routes(index))
        } else None
      }
  }
}
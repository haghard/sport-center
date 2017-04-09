package gateway

import java.io.IOException
import java.net.{ InetAddress, InetSocketAddress }
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

import akka.actor._
import java.util.concurrent.{ TimeUnit, ThreadLocalRandom }
import akka.cluster.ddata.{ LWWMapKey, LWWMap }
import akka.cluster.ddata.Replicator.Changed
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.graphite.GraphiteSender
import discovery.ReplicatedHttpRoutes.HttpRouteLine
import microservice.api.MicroserviceKernel
import akka.http.scaladsl.model.{ HttpHeader, HttpResponse, HttpRequest }
import akka.http.scaladsl.model.StatusCodes._

object ApiGateway {
  //TODO: add microservice version in url 
  val fragmentExp = """http://(\d{0,3}.\d{0,3}.\d{0,3}.\d{0,3}.):(\d{4,})([\d|/|\w|\{|\}]+)""".r

  case class Route(host: String, port: Int, pathRegex: String)

  private def updateRoutees(map: LWWMap[HttpRouteLine]) = {
    map.entries.values.toList.map(_.urls)
      .flatten
      .map {
        case fragmentExp(host, port, path) ⇒
          if (path.indexOf('{') == -1 & path.indexOf('}') == -1) {
            Route(host, port.toInt, path)
          } else {
            val param = path.substring(path.indexOf('{') + 1, path.indexOf('}'))
            Route(host, port.toInt, path.replaceAll("\\{" + param + "\\}", "(.*)"))
          }
        case other ⇒ Route("localhost", 8000, "/empty")
      }.groupBy(_.pathRegex)
  }

  def headers(headers: Seq[HttpHeader]) = headers./:(Map[String, String]()) { (acc, c) =>
    acc + (c.name() -> c.value())
  }

  def props(localAddress: String, httpPort: Int) =
    Props(classOf[ApiGateway], localAddress, httpPort).withDispatcher(MicroserviceKernel.microserviceDispatcher)
}

class ApiGateway private (address: String, httpPort: Int) extends Actor with ActorLogging {
  import gateway.ApiGateway._
  import com.codahale.metrics.{ Histogram, Counter }
  import com.codahale.metrics.graphite.GraphiteReporter

  var routees: Option[Map[String, List[Route]]] = None
  val host = context.system.settings.config.getString("graphite.host")
  val port = context.system.settings.config.getInt("graphite.port")

  //new GraphiteUDP()
  val graphite = new GraphiteSender() {
    var failures = 0
    val Encoding = "utf-8"
    val sendBuffer = (ByteBuffer allocate 512) //one command in avg takes 34 bytes
    val channel = DatagramChannel.open()
    val address = new InetSocketAddress(InetAddress.getByName(host), port)

    override def flush(): Unit = ()

    override def send(name: String, value: String, timestamp: Long): Unit = {
      try {
        sendBuffer.put(s"$name:$value|c".getBytes(Encoding))
        sendBuffer.flip()
        channel.send(sendBuffer, address)
        sendBuffer.limit(sendBuffer.capacity())
        sendBuffer.rewind()
      } catch { case e: IOException => failures += 1 }
    }

    override def isConnected: Boolean = true

    override def connect(): Unit = {}

    override def getFailures: Int = failures

    override def close(): Unit = ()
  }

  val registry = new MetricRegistry()
  //var histograms = Map[String, Histogram]().withDefault(key => registry.histogram(key))
  var counters = Map[String, Counter]().withDefault(key => registry.counter(key))

  override def preStart = {
    GraphiteReporter.forRegistry(registry)
      .build(graphite)
      .start(2, TimeUnit.SECONDS)
  }

  override def preRestart(reason: scala.Throwable, message: scala.Option[scala.Any]): scala.Unit = {
    log.info("ApiGateway was restarted and lost all routees {}", reason.getMessage)
  }

  override def receive: Receive = {
    case r @ Changed(LWWMapKey(_)) if r.dataValue.isInstanceOf[LWWMap[HttpRouteLine]] ⇒
      routees = Option(updateRoutees(r.dataValue.asInstanceOf[LWWMap[HttpRouteLine]]))
      log.info("Routees has changed: {}", routees)

    case r: HttpRequest ⇒
      val replyTo = sender()
      findRoute(r).fold {
        replyTo ! HttpResponse(NotFound, entity = s"Underling api cannot be found. The route ${r.uri.path} was not found")
      } { route: Route ⇒
        val reqUri = r.uri
        val internalUri = reqUri.withHost(route.host).withPort(route.port)
        val cmd = services.hystrix.command(
          route.pathRegex,
          replyTo, internalUri.toString, r.headers
        )
        val key = cmd.getCommandKey.toString

        //histograms(key).update(1)
        counters(key).inc()

        cmd.queue()
      }
  }

  private def findRoute(r: HttpRequest): Option[Route] = {
    routees.map { routes ⇒
      routes.keySet.find { route ⇒
        route.r.findFirstIn(r.uri.path.toString) match {
          case Some(_) ⇒ true
          case None ⇒ false
        }
      }.flatMap(randomNext)
    }.flatten
  }

  //TODO: implement other routing strategy: rnr, ... etc
  private def randomNext(routePath: String): Option[Route] =
    routees.get.get(routePath)
      .flatMap { routes ⇒
        if (routes.size > 0) {
          val size = routes.size
          val index = ThreadLocalRandom.current().nextInt(size)
          Option(routes(index))
        } else None
      }
}
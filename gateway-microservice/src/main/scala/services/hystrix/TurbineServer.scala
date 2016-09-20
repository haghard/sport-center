package hystrix

import java.net.URI
import java.nio.charset.Charset
import java.util

import akka.actor.{ Address, ActorLogging }
import com.netflix.turbine.Turbine
import com.netflix.turbine.internal.JsonUtility
import http.{ SSEvents, HystrixMetricsMicroservice }
import io.netty.buffer.{ Unpooled, ByteBuf }
import io.reactivex.netty.RxNetty
import rx.functions.Action0
import rx.lang.scala.JavaConversions._
import scala.collection.immutable
import io.reactivex.netty.protocol.http.server.{ HttpServer, HttpServerResponse, HttpServerRequest, RequestHandler }

trait TurbineServer {
  mixin: ActorLogging =>

  implicit def f2Act(f: () => Unit) = new Action0 {
    override def call(): Unit = f()
  }

  private val turbinePort = 6500

  protected var server: Option[HttpServer[ByteBuf, ByteBuf]] = None

  /**
   * Start http server on http://turbine-hostname:port/turbine.stream
   *
   * @param streams
   */
  protected def start(streams: immutable.Set[Address]) = {
    val uris = toURI(streams)
    if (server.isEmpty) {
      val local = createServer(uris)
      local.start()
      server = Some(local)
    } else {
      server.get.shutdown()
      log.info("Stop current turbine server")
      val local = createServer(uris)
      local.start()
      server = Some(local)
    }
  }

  /**
   * + 10 convention
   * @param streams
   * @return
   */
  private def toURI(streams: immutable.Set[Address]) =
    for { n <- streams; host <- n.host; port <- n.port }
      yield URI.create(s"http://$host:${port + 10}${HystrixMetricsMicroservice.hystrixStream}")

  private def serverHandler(pStreams: rx.Observable[_ <: util.Map[String, AnyRef]]) = new RequestHandler[ByteBuf, ByteBuf]() {
    override def handle(request: HttpServerRequest[ByteBuf], response: HttpServerResponse[ByteBuf]): rx.Observable[Void] = {
      response.getHeaders.setHeader("Content-Type", "text/event-stream")
      toJavaObservable(toScalaObservable(pStreams)
        .doOnUnsubscribe(() => log.info("Turbine => Unsubscribing RxNetty server connection"))
        .flatMap((map0: util.Map[String, AnyRef]) => {
          val event = SSEvents.Element(JsonUtility.mapToJson(map0))
          response.writeAndFlush(Unpooled.copiedBuffer(event.toString, Charset.defaultCharset()))
        })).asInstanceOf[rx.Observable[Void]]
    }
  }

  private def createServer(streams: Set[URI]): HttpServer[ByteBuf, ByteBuf] = {
    log.info(s"Create new turbine server with streams: [ ${
      streams.foldLeft(new StringBuilder()) { (acc, c) => acc.append(c.toString).append(",")  }
    } ]")

    import rx.lang.scala.JavaConversions._
    val pStreams: rx.Observable[_ <: util.Map[String, AnyRef]] = toScalaObservable(Turbine.aggregateHttpSSE(streams.toList: _*))
      .doOnUnsubscribe(() => log.info("Turbine => Unsubscribing aggregation."))
      .doOnSubscribe(() => log.info("Turbine => Starting aggregation"))
      .flatMap(o => o)
      .publish().refCount()
    RxNetty.createHttpServer(turbinePort, serverHandler(pStreams))
  }
}

package hystrix

import java.net.URI
import java.nio.charset.Charset
import java.util
import java.util.concurrent.TimeUnit

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

import scala.util.control.NonFatal

trait TurbineServer {
  mixin: ActorLogging =>

  implicit def lambda2Acttion(f: () => Unit) = new Action0 {
    override def call(): Unit = f()
  }

  private val turbinePort = 6500 //put it into config

  //private var server: Option[HttpServer[ByteBuf, ByteBuf]] = None

  protected def startTurbine(streams: immutable.Set[Address], server: Option[HttpServer[ByteBuf, ByteBuf]]): Option[HttpServer[ByteBuf, ByteBuf]] = {
    log.info(s"Trying to stop existing Turbine: ${server.isEmpty}")
    val uris = toURI(streams)

    try {
      server.foreach {
        _.waitTillShutdown(30, TimeUnit.SECONDS)
      }
    } catch {
      case ex: InterruptedException => log.error(ex, "Couldn't stop Turbine")
      case NonFatal(ex) =>
    }

    log.info("Sleep")
    Thread.sleep(10000)
    val httpHystrixServer = createServer(uris)
    Option(httpHystrixServer.start)
    //server = Some(httpHystrixServer)

/*
    if (server.isEmpty) {
      val local = createServer(uris)
      local.start()
      server = Some(local)
    } else {

      server.get.waitTillShutdown(10, TimeUnit.SECONDS)
      val local = createServer(uris)
      local.start()
      server = Some(local)
    }*/
  }

  private def toURI(streams: immutable.Set[Address]) =
    for {
      n <- streams
      host <- n.host
      port <- n.port
    } yield URI.create(s"http://$host:${port + 10}/${HystrixMetricsMicroservice.hystrixStream}")

  private def serverHandler(pStreams: rx.Observable[_ <: util.Map[String, AnyRef]]) = new RequestHandler[ByteBuf, ByteBuf]() {
    override def handle(request: HttpServerRequest[ByteBuf], response: HttpServerResponse[ByteBuf]): rx.Observable[Void] = {
      response.getHeaders.setHeader("Content-Type", "text/event-stream")
      toJavaObservable(toScalaObservable(pStreams)
        .doOnUnsubscribe(() => log.info("Turbine => Unsubscribing RxNetty server connection"))
        .flatMap { data: util.Map[String, AnyRef] =>
          val event = SSEvents.Element(JsonUtility.mapToJson(data))
          response.writeAndFlush(Unpooled.copiedBuffer(event.toString, Charset.defaultCharset()))
        }).asInstanceOf[rx.Observable[Void]]
    }
  }

  private def createServer(streams: Set[URI]): HttpServer[ByteBuf, ByteBuf] = {
    val uris = streams.foldLeft(new StringBuilder())((acc, c) => acc.append(c.toString).append(","))
    log.info(s"Create new Hystrix-Turbine server for streams: [$uris]")

    import rx.lang.scala.JavaConversions._
    val clients: rx.Observable[_ <: util.Map[String, AnyRef]] = toScalaObservable(Turbine.aggregateHttpSSE(streams.toList: _*))
      .doOnUnsubscribe(() => log.info("Turbine => Unsubscribing aggregation."))
      .doOnSubscribe(() => log.info("Turbine => Starting aggregation"))
      .flatMap(o => o)
      .publish().refCount()
    RxNetty.createHttpServer(turbinePort, serverHandler(clients))
  }
}
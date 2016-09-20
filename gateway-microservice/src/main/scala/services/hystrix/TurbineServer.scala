package hystrix

import java.net.URI
import java.nio.charset.Charset
import java.util
import akka.actor.{ Address, ActorLogging }
import akka.event.LoggingAdapter
import com.netflix.turbine.Turbine
import com.netflix.turbine.internal.JsonUtility
import http.{ SSEvents, HystrixMetricsMicroservice }
import io.netty.buffer.{ Unpooled, ByteBuf }
import io.reactivex.netty.RxNetty
import rx.functions.Action0
import rx.lang.scala.JavaConversions._
import scala.annotation.tailrec
import scala.collection.immutable
import io.reactivex.netty.protocol.http.server.{ HttpServer, HttpServerResponse, HttpServerRequest, RequestHandler }

import scala.util.{Success, Try, Failure}
import scala.util.control.NonFatal


object TurbineServer {
  def executeWithRetry[T](n: Int)(log: LoggingAdapter, f: ⇒ T) = retry(n)(log, f)

  @tailrec private def retry[T](n: Int)(log: LoggingAdapter, f: ⇒ T): T = {
    log.info(s"Attempt to stop Turbine: $n")
    Try(f) match {
      case Success(_) => null.asInstanceOf[T]
        log.info("Turbine has been stopped") //already stopped
        null.asInstanceOf[T]
      case _ if n > 1 ⇒ retry(n - 1)(log, f)
      case Failure(ex) if (ex.isInstanceOf[java.lang.IllegalStateException]) =>
        log.info(ex.getMessage) //already stopped
        null.asInstanceOf[T]
      case Failure(ex) ⇒
        log.error(ex, "Couldn't stop Turbine")
        throw ex
    }
  }
}

trait TurbineServer {
  mixin: ActorLogging =>
  import TurbineServer._

  implicit def lambda2Acttion(f: () => Unit) = new Action0 {
    override def call(): Unit = f()
  }

  private val turbinePort = 6500 //put it into config

  //private var server: Option[HttpServer[ByteBuf, ByteBuf]] = None

  protected def startTurbine(streams: immutable.Set[Address], server: Option[HttpServer[ByteBuf, ByteBuf]]): Option[HttpServer[ByteBuf, ByteBuf]] = {
    log.info(s"Don we have turbine on this node: ${server.nonEmpty}")
    val uris = toURI(streams)

    executeWithRetry(5)(log, server.foreach(_.shutdown))

    /*try {
      server.foreach(_.shutdown())
    } catch {
      case ex: java.lang.IllegalStateException =>
      case ex: InterruptedException => log.error(ex, "Couldn't stop Turbine")
      case NonFatal(ex) => log.error(ex, "Couldn't stop Turbine. Unexpected error")
    }*/

    val urisLine = streams.foldLeft(new StringBuilder())((acc, c) => acc.append(c.toString).append(","))
    log.info(s"Create new Hystrix-Turbine server for streams: [$urisLine]")
    Thread.sleep(3000) //to make sure

    val httpHystrixServer = createServer(uris)
    log.info(s"Hystrix-Turbine server has been created:[$urisLine]")
    Option(httpHystrixServer.start)
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
    import rx.lang.scala.JavaConversions._
    val clients: rx.Observable[_ <: util.Map[String, AnyRef]] = toScalaObservable(Turbine.aggregateHttpSSE(streams.toList: _*))
      .doOnUnsubscribe(() => log.info("Turbine => Unsubscribing aggregation."))
      .doOnSubscribe(() => log.info("Turbine => Starting aggregation"))
      .flatMap(o => o)
      .publish().refCount()
    RxNetty.createHttpServer(turbinePort, serverHandler(clients))
  }
}
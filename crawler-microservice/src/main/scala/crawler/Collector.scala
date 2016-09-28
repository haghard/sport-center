package crawler

import akka.actor._
import microservice.crawler.{ NbaResult, SuccessCollected, TimeOut }
import microservice.domain._

import scala.concurrent.duration._

object Collector {
  def props(urls: List[String]) = Props(new Collector(urls))
    .withDispatcher("crawler-dispatcher")
}

class Collector(urls: List[String]) extends Actor with ActorLogging {
  context.setReceiveTimeout(20 seconds)

  var batch = List.empty[NbaResult]

  override def receive = running(urls)

  def running(batchUrls: List[String]): Receive = {
    case results: (String, List[NbaResult]) ⇒ context become dequeue(results, batchUrls)
    case ReceiveTimeout ⇒ context.parent ! TimeOut(batchUrls, batch)
  }

  def dequeue(results: (String, List[NbaResult]), batchUrls: List[String]): Receive = {
    batch :::= results._2
    val restUrls = (batchUrls copyWithout results._1)
    if (restUrls.isEmpty) {
      context.parent ! SuccessCollected(batch)
      log.info("WebCollector complete batch")
      context stop self
    }
    running(restUrls)
  }
}
package crawler

import akka.actor._
import microservice.crawler.{ NbaResult, SuccessCollected, TimeOut }
import microservice.domain._

import scala.concurrent.duration._

object Aggregator {
  def props(urls: List[String]) = Props(new Aggregator(urls))
}

class Aggregator(urls: List[String]) extends Actor with ActorLogging {
  private var batch = List.empty[NbaResult]

  context.setReceiveTimeout(10 seconds)

  override def receive = running(urls)

  def running(batchUrls: List[String]): Receive = {
    case results: (String, List[NbaResult]) ⇒ context become dequeue(results, batchUrls)
    case ReceiveTimeout                     ⇒ context.parent ! TimeOut(batchUrls, batch)
  }

  def dequeue(results: (String, List[NbaResult]), batchUrls: List[String]): Receive = {
    batch :::= results._2
    val restUrls = batchUrls.copyWithout(results._1)
    if (restUrls.isEmpty) {
      context.parent ! SuccessCollected(batch)
      log.info("Aggregator complete batch")
      context.stop(self)
    }
    running(restUrls)
  }
}
package hystrix

import akka.actor.{ ActorLogging, Props }
import akka.stream.actor.{ ActorPublisher, ActorPublisherMessage }
import com.netflix.hystrix.contrib.metrics.eventstream.HystrixMetricsPoller

import scala.annotation.tailrec

object HystrixMetricsPublisher {
  def props = Props(new HystrixMetricsPublisher)
}

class HystrixMetricsPublisher extends ActorPublisher[Vector[String]] with ActorLogging {
  private val delay = 500
  private val listener = new MetricJsonListener(1000)
  private val poller = new HystrixMetricsPoller(listener, delay)

  override def preStart = poller.start()

  override def receive: Receive = {
    case ActorPublisherMessage.Request(n) if (isActive && totalDemand > 0) =>
      loop(poller, listener, n)

    case ActorPublisherMessage.SubscriptionTimeoutExceeded ⇒
      poller.shutdown()
      onComplete()
      context.stop(self)

    case ActorPublisherMessage.Cancel ⇒
      log.info(s"Hystrix metric client disconnected")
      poller.shutdown()
      onComplete()
      context.stop(self)
  }

  @tailrec final def loop(poller: HystrixMetricsPoller, listener: MetricJsonListener, n: Long): Unit = {
    Thread.sleep(delay)
    if (n > 0 && poller.isRunning) {
      listener.getJsonMetrics match {
        case Vector() => onNext(Vector("ping: "))
        case other => onNext(other)
      }
      loop(poller, listener, n - 1)
    } else ()
  }
}
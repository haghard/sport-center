package microservice.http

import akka.actor.{ ActorLogging, Actor, ActorRef, Props }
import scala.concurrent.duration.FiniteDuration

object Limiter {
  case object Acquire
  case object Progress
  case object Release

  def props(maxAvailableTokens: Int, tokenRefreshPeriod: FiniteDuration, tokenRefreshAmount: Int): Props =
    Props(new Limiter(maxAvailableTokens, tokenRefreshPeriod, tokenRefreshAmount))
}

class Limiter(val maxAvailableTokens: Int, val tokenRefreshPeriod: FiniteDuration, val tokenRefreshAmount: Int) extends Actor
    with ActorLogging {

  import akka.actor.Status
  import context.dispatcher
  import scala.collection._
  import microservice.http.Limiter._

  private var waitQueue = immutable.Queue.empty[ActorRef]
  private var permitTokens = maxAvailableTokens
  private val replenishTimer = context.system.scheduler.schedule(
    initialDelay = tokenRefreshPeriod,
    interval = tokenRefreshPeriod,
    receiver = self,
    Release)

  override def receive: Receive = open

  private val open: Receive = {
    case Release =>
      permitTokens = math.min(permitTokens + tokenRefreshAmount, maxAvailableTokens)
    case Acquire =>
      log.info("Acquire")
      permitTokens -= 1
      sender() ! Progress
      if (permitTokens == 0) {
        log.info("Req become closed")
        context become closed
      }
  }

  private val closed: Receive = {
    case Release =>
      permitTokens = math.min(permitTokens + tokenRefreshAmount, maxAvailableTokens)
      releaseWaiting()
    case Acquire =>
      log.info(s"Block ${sender()}")
      waitQueue = waitQueue.enqueue(sender())
  }

  private def releaseWaiting(): Unit = {
    val (toBeReleased, remainingQueue) = waitQueue.splitAt(permitTokens)
    waitQueue = remainingQueue
    permitTokens -= toBeReleased.size
    toBeReleased.foreach { act => { log.info(s"Released - $act"); act ! Progress } }
    if (permitTokens > 0) {
      context become open
    }
  }

  override def postStop(): Unit = {
    replenishTimer.cancel()
    waitQueue foreach (_ ! Status.Failure(new IllegalStateException("limiter stopped")))
  }
}

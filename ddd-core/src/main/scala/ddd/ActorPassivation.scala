package ddd

import scala.concurrent.duration._
import akka.actor.{ PoisonPill, ReceiveTimeout, Actor }

import scala.concurrent.duration.Duration

case class PassivationConfig(passivationMsg: Any = PoisonPill, inactivityTimeout: Duration = 30.minutes)

trait ActorPassivation extends Actor {

  def pc: PassivationConfig

  override def preStart() {
    context.setReceiveTimeout(pc.inactivityTimeout)
  }

  override def unhandled(message: Any) {
    message match {
      case ReceiveTimeout => context.parent ! pc.passivationMsg
      case _ => super.unhandled(message)
    }
  }
}
package microservice.api

import akka.actor.ActorSystem

trait BootableMicroservice {

  def system: ActorSystem

  def startup(): Unit

  def shutdown(): Unit

  def environment: String
}
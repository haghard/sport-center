package domain

import akka.actor.PoisonPill
import domain.update.NbaChangeDataCaptureSubscriber
import akka.contrib.pattern.ClusterSingletonManager
import microservice.api.{ MicroserviceKernel, BootableMicroservice }

trait DomainSupport extends BootableMicroservice {

  implicit val sys = system

  abstract override def startup(): Unit = {
    system.actorOf(ClusterSingletonManager.props(
      singletonProps = NbaChangeDataCaptureSubscriber.props,
      singletonName = "change-set-subscriber",
      terminationMessage = PoisonPill,
      role = Some(MicroserviceKernel.DomainRole)),
      name = "singleton-change-set-subscriber")
    super.startup()
  }
}

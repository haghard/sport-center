package domain

import akka.actor.PoisonPill
import domain.update.NbaChangeDataCaptureSubscriber
import akka.contrib.pattern.ClusterSingletonManager
import microservice.api.{ BootableClusterNode, BootableMicroservice }

trait DomainSupport extends BootableMicroservice {

  implicit val sys = system

  abstract override def startup(): Unit = {
    system.actorOf(ClusterSingletonManager.props(
      singletonProps = NbaChangeDataCaptureSubscriber.props,
      singletonName = "change-set-subscriber",
      terminationMessage = PoisonPill,
      role = Some(BootableClusterNode.MicroserviceRole)),
      name = "singleton-change-set-subscriber")
    super.startup()
  }
}

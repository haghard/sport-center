package domain

import akka.actor.PoisonPill
import domain.update.ChangeSetSubscriber
import akka.contrib.pattern.ClusterSingletonManager
import microservice.api.{ BootableClusterNode, BootableMicroservice }

trait DomainSupport extends BootableMicroservice {

  abstract override def startup(): Unit = {
    Domains(system).start()

    system.actorOf(ClusterSingletonManager.props(
      singletonProps = ChangeSetSubscriber.props,
      singletonName = "change-set-subscriber",
      terminationMessage = PoisonPill,
      role = Some(BootableClusterNode.MicroserviceRole)),
      name = "singleton-change-set-subscriber")

    super.startup()
  }
}

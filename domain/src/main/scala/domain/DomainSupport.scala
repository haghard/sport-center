package domain

import akka.actor.PoisonPill
import domain.update.WriterGuardian
import microservice.SystemSettings
import microservice.api.{ MicroserviceKernel, BootableMicroservice }
import akka.cluster.singleton.{ ClusterSingletonManagerSettings, ClusterSingletonManager }

trait DomainSupport extends BootableMicroservice {
  this: SystemSettings =>

  implicit val sys = system

  abstract override def startup(): Unit = {
    system.actorOf(ClusterSingletonManager.props(
      singletonProps = WriterGuardian.props(settings),
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(system)
        .withRole(MicroserviceKernel.DomainRole)
        .withSingletonName("change-set-subscriber")
    ))
    super.startup()
  }
}

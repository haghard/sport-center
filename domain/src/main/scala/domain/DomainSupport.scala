package domain

import akka.actor.PoisonPill
import domain.update.NbaChangeDataCaptureSubscriber
import microservice.api.{ MicroserviceKernel, BootableMicroservice }
import akka.cluster.singleton.{ ClusterSingletonManagerSettings, ClusterSingletonManager }

trait DomainSupport extends BootableMicroservice {

  implicit val sys = system

  abstract override def startup(): Unit = {
    system.actorOf(ClusterSingletonManager.props(
      singletonProps = NbaChangeDataCaptureSubscriber.props,
      //singletonName = "change-set-subscriber",
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(system)
        .withRole(MicroserviceKernel.DomainRole)
        .withSingletonName("change-set-subscriber")
    //role = Some(MicroserviceKernel.DomainRole)),
    //name = "singleton-change-set-subscriber"
    ))
    super.startup()
  }
}

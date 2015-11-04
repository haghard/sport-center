package discovery

import akka.actor.{ PoisonPill, Props }
import akka.cluster.ddata.DistributedData
import akka.cluster.singleton.{ ClusterSingletonManagerSettings, ClusterSingletonManager }
import microservice.api.{ MicroserviceKernel, ClusterNetworkSupport, BootableMicroservice }

trait ServiceRegistryCleanerSupport extends BootableMicroservice {
  self: ClusterNetworkSupport ⇒

  import scala.concurrent.duration._

  private val config = system.settings.config.getConfig("discovery")

  abstract override def startup(): Unit = {
    system.actorOf(
      ClusterSingletonManager.props(
        singletonProps = Props(new ServiceDiscoveryGuardian(config.getDuration("ops-timeout", SECONDS).second,
          Option(MicroserviceKernel.DomainRole), DistributedData(system).replicator) with OnClusterLeaveKeysCleaner),
        terminationMessage = PoisonPill,
        settings = ClusterSingletonManagerSettings(system)
          .withSingletonName("keys-guardian")
          .withRole(MicroserviceKernel.GatewayRole)
      ))

    super.startup()
  }
}


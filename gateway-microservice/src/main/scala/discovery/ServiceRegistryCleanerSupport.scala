package discovery

import akka.actor.{ PoisonPill, Props }
import akka.contrib.datareplication.DataReplication
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
          Some(MicroserviceKernel.DomainRole), DataReplication(system).replicator) with OnClusterLeaveKeysCleaner),
        //singletonName = "keys-guardian",
        terminationMessage = PoisonPill,
        settings = ClusterSingletonManagerSettings(system)
          .withSingletonName("keys-guardian")
          .withRole(MicroserviceKernel.GatewayRole)
      //role = Some(MicroserviceKernel.GatewayRole)),
      //name = "keys-guardian-singleton"
      ))

    super.startup()
  }
}


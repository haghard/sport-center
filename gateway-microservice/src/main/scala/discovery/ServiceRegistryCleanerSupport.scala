package discovery

import akka.actor.{ PoisonPill, Props }
import akka.contrib.datareplication.DataReplication
import akka.contrib.pattern.ClusterSingletonManager
import microservice.api.{ BootableClusterNode, ClusterNetworkSupport, BootableMicroservice }

trait ServiceRegistryCleanerSupport extends BootableMicroservice {
  self: ClusterNetworkSupport ⇒

  import scala.concurrent.duration._

  private val config = system.settings.config.getConfig("discovery")

  abstract override def startup(): Unit = {
    system.actorOf(ClusterSingletonManager.props(
      singletonProps = Props(new ServiceDiscoveryGuardian(config.getDuration("ops-timeout", SECONDS).second,
        Some(BootableClusterNode.MicroserviceRole), DataReplication(system).replicator) with OnClusterLeaveKeysCleaner),
      singletonName = "keys-guardian",
      terminationMessage = PoisonPill,
      role = Some(BootableClusterNode.RoutingLayerRole)),
      name = "keys-guardian-singleton")

    super.startup()
  }
}


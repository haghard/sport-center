package hystrix

import akka.actor.PoisonPill
import akka.contrib.pattern.ClusterSingletonManager
import microservice.api.{ MicroserviceKernel, BootableMicroservice, ClusterNetworkSupport }

/**
 *
 *
 */
trait HystrixTurbineSupport extends BootableMicroservice {
  self: ClusterNetworkSupport ⇒

  abstract override def startup(): Unit = {
    akka.cluster.Cluster(system).registerOnMemberUp {
      system.actorOf(ClusterSingletonManager.props(
        singletonProps = HystrixTurbineManager.props,
        singletonName = "hystrix-turbine-manager",
        terminationMessage = PoisonPill,
        role = Some(MicroserviceKernel.GatewayRole)),
        name = "singleton-hystrix-turbine-manager")
    }
    super.startup()
  }
}
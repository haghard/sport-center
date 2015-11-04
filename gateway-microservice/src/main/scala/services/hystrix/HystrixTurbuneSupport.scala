package hystrix

import akka.actor.PoisonPill
import akka.cluster.singleton.{ ClusterSingletonManagerSettings, ClusterSingletonManager }
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
        terminationMessage = PoisonPill,
        settings = ClusterSingletonManagerSettings(system)
          .withRole(MicroserviceKernel.GatewayRole)
          .withSingletonName("hystrix-turbine-manager")
      ))
    }
    super.startup()
  }
}
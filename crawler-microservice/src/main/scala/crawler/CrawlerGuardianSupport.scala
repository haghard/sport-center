package crawler

import akka.actor._
import microservice.SystemSettings
import crawler.writer.CrawlerGuardian
import akka.cluster.singleton.{ ClusterSingletonManagerSettings, ClusterSingletonManager }
import microservice.api.{ MicroserviceKernel, BootableMicroservice }

trait CrawlerGuardianSupport extends BootableMicroservice with SystemSettings {

  abstract override def startup(): Unit = {
    system.actorOf(ClusterSingletonManager.props(
      singletonProps = CrawlerGuardian.props(settings),
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(system)
        .withRole(MicroserviceKernel.CrawlerRole)
        .withSingletonName("crawler-guardian")
    ), name = "singleton-crawler-guardian")
    super.startup()
  }
}
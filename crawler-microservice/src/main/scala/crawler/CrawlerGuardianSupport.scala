package crawler

import akka.actor._
import crawler.writer.CrawlerGuardian
import microservice.SystemSettings
import microservice.api.{ MicroserviceKernel, BootableMicroservice }
import akka.cluster.singleton.{ ClusterSingletonManagerSettings, ClusterSingletonManager }

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
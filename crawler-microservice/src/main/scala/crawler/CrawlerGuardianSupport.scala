package crawler

import akka.actor._
import crawler.writer.CrawlerGuardian
import microservice.SystemSettings
import microservice.api.{ MicroserviceKernel, BootableMicroservice }
import akka.contrib.pattern.ClusterSingletonManager

trait CrawlerGuardianSupport extends BootableMicroservice with SystemSettings {

  abstract override def startup(): Unit = {
    system.actorOf(ClusterSingletonManager.props(
      singletonProps = CrawlerGuardian.props(settings),
      singletonName = "crawler-guardian",
      terminationMessage = PoisonPill,
      role = Some(MicroserviceKernel.CrawlerRole)),
      name = "singleton-crawler-guardian")

    super.startup()
  }
}
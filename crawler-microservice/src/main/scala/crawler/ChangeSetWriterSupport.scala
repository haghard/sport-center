package crawler

import akka.actor._
import akka.contrib.pattern.ClusterSingletonManager
import crawler.writer.ChangeSetBatchWriter
import microservice.SystemSettings
import microservice.api.BootableClusterNode._
import microservice.api.BootableMicroservice

trait ChangeSetWriterSupport extends BootableMicroservice with SystemSettings {

  abstract override def startup(): Unit = {
    system.actorOf(ClusterSingletonManager.props(
      singletonProps = ChangeSetBatchWriter.props(settings),
      singletonName = "campaign-writer",
      terminationMessage = PoisonPill,
      role = Some(CrawlerRole)),
      name = "singleton-campaign-writer")

    super.startup()
  }
}
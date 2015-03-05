/*
package crawler

import akka.actor.PoisonPill
import microservice.SystemSettings
import microservice.api.BootableClusterNode._
import microservice.api.BootableMicroservice
import akka.contrib.pattern.{ ClusterSingletonManager, ClusterSingletonProxy }

trait CrawlerActors extends BootableMicroservice with SystemSettings {

  abstract override def startup = {

    system.actorOf(ClusterSingletonManager.props(
      singletonProps = CrawlerMaster.props(settings),
      singletonName = settings.clusterSingletons.crawlerPaths.singletonName,
      terminationMessage = PoisonPill,
      role = Some(CrawlerRole)),
      name = settings.clusterSingletons.crawlerPaths.name)

    system.actorOf(ClusterSingletonProxy.props(singletonPath = settings.clusterSingletons.crawlerPaths.originalPath,
      role = Some(CrawlerRole)),
      name = settings.clusterSingletons.crawlerPaths.proxyName)

    super.startup
  }
}
*/ 
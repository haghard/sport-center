package domain

import akka.actor.PoisonPill
import microservice.SystemSettings
import scala.concurrent.Await
import scala.concurrent.duration._
import domain.update.DomainWriterSupervisor
import microservice.api.{ MicroserviceKernel, BootableMicroservice }
import akka.cluster.singleton.{ ClusterSingletonManagerSettings, ClusterSingletonManager }

import scala.util.{ Failure, Success }

trait DomainSupport extends BootableMicroservice { this: SystemSettings =>
  implicit val sys = system
  implicit val to = 5 seconds
  implicit val ex = system.dispatchers.lookup("scheduler-dispatcher")

  abstract override def startup(): Unit = {
    system.actorOf(ClusterSingletonManager.props(
      singletonProps = DomainWriterSupervisor.props(settings),
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(system)
        .withRole(MicroserviceKernel.DomainRole)
        .withSingletonName("domain")
    ))
    /*
    val singletonProxy = system.actorOf(
      ClusterSingletonProxy.props("/user/domain",
        ClusterSingletonProxySettings.create(system)
          .withRole(MicroserviceKernel.DomainRole)
      ), "domainProxy")
    */
    super.startup()
  }

  abstract override def shutdown() = {
    val f = (ShardedDomain(system) gracefulShutdown)
    f.onComplete {
      case Success(u)  => sys.log.info("★ ★ ★  Local shard has been stopped ★ ★ ★ ")
      case Failure(ex) => sys.log.info("★ ★ ★ Local shard stop error {} ★ ★ ★ ", ex.getMessage)
    }
    Await.result(f, to)
    super.shutdown()
  }
}
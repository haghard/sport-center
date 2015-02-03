package discovery

import akka.actor.{ Actor, ActorLogging }
import akka.cluster.Member
import akka.util.Timeout
import discovery.ServiceDiscovery.UnsetAddress
import microservice.api.ClusterMembershipAware

import scala.collection.mutable
import scala.concurrent.duration._
import scalaz.{ -\/, \/- }

trait OnClusterLeaveKeysCleaner extends ClusterMembershipAware {
  mixin: Actor with ActorLogging {
    def role: Option[String]
    def nodes: mutable.Set[akka.actor.Address]
  } ⇒

  abstract override def receiveMemberRemoved(m: Member): Unit = {
    super.receiveMemberRemoved(m)

    implicit val t = Timeout(3.seconds)
    implicit val ex = context.system.dispatchers.lookup("scheduler-dispatcher")

    ServiceDiscovery(context.system)
      .deleteAll(UnsetAddress(m.address.toString))
      .map {
        case \/-(r)     ⇒ log.info(s"Service with key ${m.address} was successfully unregistered")
        case -\/(error) ⇒ log.info(s"Service ${m.address} unregistered error $error")
      }
  }
}

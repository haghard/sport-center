package discovery

import akka.actor.{ Actor, ActorLogging, ActorRef, Address }
import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

class ServiceDiscoveryGuardian private[discovery] (val timeout: FiniteDuration,
  val role: Option[String],
  val replicator: ActorRef,
  var nodes: mutable.Set[Address] = new mutable.HashSet[Address])
    extends Actor with ActorLogging {

  mixin: { def withClusterEvents: akka.actor.Actor.Receive } ⇒

  override def receive: Receive = withClusterEvents
}

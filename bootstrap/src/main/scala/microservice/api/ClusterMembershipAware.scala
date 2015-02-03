package microservice.api

import akka.actor.Actor
import akka.cluster.ClusterEvent._
import akka.cluster.{Cluster, Member}
import scala.collection.mutable

trait ClusterMembershipAware {
  mixin: Actor {
    def role: Option[String]
    def nodes: mutable.Set[akka.actor.Address]
  } ⇒

  private implicit val cluster = Cluster(context.system)

  protected val selfAddress = cluster.selfAddress

  override def preStart(): Unit =
    cluster.subscribe(self, classOf[MemberEvent], classOf[ReachabilityEvent])

  override def postStop() =
    cluster unsubscribe self

  protected def withClusterEvents(behavior: Receive): Receive = behavior orElse withClusterEvents

  protected val withClusterEvents: Receive = {
    case state: CurrentClusterState ⇒ state.members.foreach { receiveMemberUp }
    case MemberUp(m)                ⇒ receiveMemberUp(m)
    case MemberRemoved(m, _)        ⇒ receiveMemberRemoved(m)
    case ReachableMember(m)         ⇒ receiveMemberUp(m)
    case UnreachableMember(m)       ⇒ receiveMemberRemoved(m)
  }

  private def receiveMemberUp(m: Member): Unit =
    if (matchingRole(m) && m.address != selfAddress) {
      nodes += (m.address)
    }

  def receiveMemberRemoved(m: Member): Unit = {
    if (m.address == selfAddress)
      context stop self
    else if (matchingRole(m)) {
      nodes -= m.address
    }
  }

  private def matchingRole(m: Member) = role.forall(m.hasRole(_))
}
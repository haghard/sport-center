package microservice.api

import akka.actor._
import akka.cluster.ClusterEvent._
import akka.cluster.{Member, MemberStatus}

import scala.collection.mutable
import scala.concurrent.forkjoin.ThreadLocalRandom

/**
 *
 *
 */
trait ClusterRoleTracker {
  mixin: Actor with ActorLogging {
    def trackableRole: String
    def scheduleJob: Unit
  } ⇒

  private var available = false

  private val trackableNodes = mutable.HashSet[Address]()

  protected def withClusterEvents(behavior: Receive): Receive = behavior orElse clusterEvents

  private def ++(m: Member) = {
    if (m.hasRole(trackableRole)) {
      trackableNodes += m.address
      if(!available) {
        scheduleJob
      }
    }
  }

  private def --(m: Member) = {
    if (m.hasRole(trackableRole))
      trackableNodes -= m.address

    if (trackableNodes.size == 0) {
      log.info("Updates are impossible. Cluster needs at least one Crawler node for continue")
      available = false
    }
  }

  protected def randomTrackableNodes: Option[Address] = {
    if(!trackableNodes.isEmpty)
      Some(trackableNodes.toIndexedSeq(ThreadLocalRandom.current.nextInt(trackableNodes.size)))
    else
      None
  }

  private def clusterEvents: Receive = {
    case state: CurrentClusterState ⇒
      state.members.foreach { m ⇒
        if (m.hasRole(trackableRole) && m.status == MemberStatus.Up) {
          trackableNodes += m.address
          available = true
        }
      }

    case MemberUp(m)          ⇒ ++(m)
    case MemberRemoved(m, _)  ⇒ --(m)
  }
}
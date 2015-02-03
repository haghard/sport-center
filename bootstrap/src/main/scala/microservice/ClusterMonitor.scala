package microservice

import akka.actor.{ Props, Actor }
import microservice.ClusterMonitor.GetNodes
import microservice.api.ClusterMembershipAware

import scala.collection.mutable

object ClusterMonitor {

  case object GetNodes

  def props(role: Option[String]) = Props(new ClusterMonitor(role))

}

class ClusterMonitor private (val role: Option[String],
  val nodes: mutable.Set[akka.actor.Address] = mutable.Set.empty[akka.actor.Address]) extends Actor
    with ClusterMembershipAware {

  private def read: Receive = {
    case GetNodes =>
      //Convention: http port for loadbalancer nodes is akka system port + 10
      sender() ! (nodes.map(a => a.copy(port = a.port.map(_ + 10)))).toVector
  }

  override def receive = withClusterEvents { read }
}

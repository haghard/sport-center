package microservice

import akka.actor.{ActorLogging, Props, Actor}
import microservice.ClusterMonitor.GetHttpNodes
import microservice.api.ClusterMembershipAware
import scala.collection.mutable

object ClusterMonitor {

  case object GetHttpNodes

  def props(role: Option[String]) = Props(new ClusterMonitor(role))
}

class ClusterMonitor private (val role: Option[String], val nodes: mutable.Set[akka.actor.Address] = mutable.Set.empty[akka.actor.Address])
    extends Actor with ActorLogging with ClusterMembershipAware {

  private def read: Receive = {
    case GetHttpNodes =>
      //Convention: http port for gateway nodes is akka system port + 10
      val httpGateways = nodes.map(a => a.copy(port = a.port.map(_ + 11))).toVector
      log.info(httpGateways.mkString(" - "))
      sender() ! httpGateways
  }

  override def receive = withClusterEvents(read)
}

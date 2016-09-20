package hystrix

import akka.cluster.ClusterEvent._
import akka.cluster.{ Cluster, Member }
import microservice.api.MicroserviceKernel
import akka.actor.{ Actor, ActorLogging, Address, Props }

import scala.collection.immutable

object HystrixTurbineManager {

  def props() = Props(new HystrixTurbineManager)
    .withDispatcher(MicroserviceKernel.microserviceDispatcher)
}

class HystrixTurbineManager extends Actor with ActorLogging with TurbineServer {
  val selfAddress = Cluster(context.system).selfAddress
  private var nodes = immutable.Set.empty[Address]

  private val hystrixRole = MicroserviceKernel.GatewayRole

  implicit val cluster = Cluster(context.system)

  override def preStart() =
    cluster.subscribe(self, classOf[MemberEvent], classOf[ReachabilityEvent])

  override def postStop() =
    cluster.unsubscribe(self)

  override def receive: Receive = {
    case state: CurrentClusterState ⇒ +++(state.members)
    case MemberUp(m)                ⇒ ++(m)
    case MemberRemoved(m, _)        ⇒ --(m)
  }

  private def +++(members: immutable.Set[Member]) = {
    val members0 = members.filter(_.hasRole(hystrixRole)).map(_.address)
    nodes = nodes ++ members0
    startTurbine(nodes)
  }

  private def --(m: Member) =
    if (m.hasRole(hystrixRole)) {
      log.info(s"HystrixTurbine MemberRemoved: ${m}" )
      nodes = nodes - m.address
      startTurbine(nodes)
    }

  private def ++(m: Member) =
    if (m.hasRole(hystrixRole)) {
      log.info(s"HystrixTurbine MemberUp: ${m}")
      nodes = nodes + m.address
      startTurbine(nodes)
    }
}
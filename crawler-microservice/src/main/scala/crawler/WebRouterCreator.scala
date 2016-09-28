package crawler

import akka.routing.{ FromConfig, RoundRobinPool }
import akka.actor.{ ActorContext, ActorRef, Props }
import akka.cluster.routing.{ ClusterRouterPool, ClusterRouterPoolSettings }

trait WebRouterCreator {
  val routerName = "webRouter"
  val dispatcher = "crawler-dispatcher"

  def routerNodeRole: String

  def createRouter: ActorRef
}

trait ProgrammaticallyCreator extends WebRouterCreator {
  def context: ActorContext

  def teams: Seq[String]

  private val routerProps = ClusterRouterPool(
    RoundRobinPool(nrOfInstances = 25),
    ClusterRouterPoolSettings(
      totalInstances = 100,
      maxInstancesPerNode = 5,
      allowLocalRoutees = true,
      useRole = Some(routerNodeRole)
    )
  ).props(Props(new WebGetter(teams))).withDispatcher(dispatcher)

  override lazy val createRouter = context.actorOf(routerProps, routerName)
}

trait FromConfigCreator extends WebRouterCreator {
  def context: ActorContext

  def teams: Seq[String]

  override lazy val createRouter =
    context.actorOf(FromConfig.props(WebGetter.props(teams).withDispatcher(dispatcher)), routerName)
}

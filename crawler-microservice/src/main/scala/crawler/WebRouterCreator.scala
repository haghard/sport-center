package crawler

import akka.actor.{ ActorContext, ActorRef, Props }
import akka.cluster.routing.{ ClusterRouterPool, ClusterRouterPoolSettings }
import akka.routing.{ FromConfig, RoundRobinPool }

trait WebRouterCreator {

  protected val dispatcher = "io-dispatcher"
  protected val routerName = "webRouter"

  def routerNodeRole: String

  def createRouter: ActorRef
}

trait ProgrammaticallyCreator extends WebRouterCreator {
  mixin: { def context: ActorContext; def teams: Seq[String] } ⇒

  private val routerProps = ClusterRouterPool(
    RoundRobinPool(nrOfInstances = 25),
    ClusterRouterPoolSettings(
      totalInstances = 100,
      maxInstancesPerNode = 5,
      allowLocalRoutees = true,
      useRole = Some(routerNodeRole))
  ).props(Props(new WebGetter(teams))).withDispatcher(dispatcher)

  override lazy val createRouter: ActorRef =
    context.actorOf(routerProps, name = routerName)
}

trait ConfigCreator extends WebRouterCreator {
  self: { def context: ActorContext; def teams: Seq[String] } ⇒

  override lazy val createRouter: ActorRef =
    context.actorOf(FromConfig.props(WebGetter.props(teams).withDispatcher(dispatcher)), name = routerName)
}

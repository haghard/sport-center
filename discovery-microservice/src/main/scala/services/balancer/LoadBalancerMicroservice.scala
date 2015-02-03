package services.balancer

import akka.http.model.StatusCodes._
import akka.http.model.Uri.{ Host ⇒ HostHeader }
import akka.http.model.headers.Host
import akka.http.model.{ HttpResponse }
import discovery.ServiceDiscovery
import microservice.api.{ BootableMicroservice, ClusterNetworkSupport }
import microservice.http.RestApi
import services.discovery.DiscoveryMicroservice

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import akka.pattern.ask

trait LoadBalancerMicroservice extends DiscoveryMicroservice {
  mixin: ClusterNetworkSupport with BootableMicroservice ⇒

  override val name = "LoadBalancerMicroservice"

  implicit val proxyTimeout = akka.util.Timeout(3 seconds)

  /*
   * If this actors failed so we lose all routes
   * We must to provide supervisor with resume strategy
   */
  private val balancer = system.actorOf(
    ApiLoadBalancer.props(httpDispatcher, localAddress, httpPort), "balancer")

  abstract override def configureApi() =
    super.configureApi() ~
      RestApi(Option { ec: ExecutionContext ⇒ balancerRoute(ec) },
        Option { () ⇒
          ServiceDiscovery(system).subscribe(balancer)
          system.log.info(s"\n★ ★ ★  [$name] was started on $httpPrefixAddress  ★ ★ ★")
        },
        Option { () ⇒ system.log.info(s"\n★ ★ ★  [$name] was stopped on $httpPrefixAddress  ★ ★ ★") })

  private def balancerRoute(implicit ec: ExecutionContext) =
    pathPrefix(pathPrefix) {
      path(Segments) { path ⇒
        ctx ⇒
          ctx complete {
            balancer
              .ask(ctx.request)
              .mapTo[HttpResponse]
              .recover {
                case ex: Exception ⇒ HttpResponse(InternalServerError, List(Host(HostHeader(localAddress), httpPort)), ex.getMessage)
              }
          }
      }
    }
}
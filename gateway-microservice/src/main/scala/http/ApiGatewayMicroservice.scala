package http

import discovery.ServiceDiscovery
import gateway.ApiGateway
import microservice.SystemSettings
import microservice.http.RestApiJunction
import microservice.api.{ BootableMicroservice, ClusterNetworkSupport }
import akka.http.scaladsl.model.headers.Host
import akka.http.scaladsl.server._
import akka.http.scaladsl.model.Uri.{ Host => HostHeader }
import akka.pattern.ask
import spray.json._
import scala.concurrent.duration._
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes._

import scala.concurrent.{ Future, ExecutionContext }

trait ApiGatewayMicroservice extends HystrixMetricsMicroservice {
  mixin: ClusterNetworkSupport with BootableMicroservice ⇒

  override val name = "ApiGatewayMicroservice"

  implicit val proxyTimeout = akka.util.Timeout(3 seconds)

  private val gateway = system.actorOf(ApiGateway.props(externalAddress, httpPort), "gateway")

  private def curl(method: String, resourcePath: String) =
    s"curl -i -X $method http://$externalAddress:$httpPort/$resourcePath"

  /*
   * If this actor fails we will lost all routes
   * We must to provide supervisor with resume strategy
   */
  abstract override def configureApi() =
    super.configureApi() ~
      RestApiJunction(Option { ec: ExecutionContext ⇒ gatewayRoute(ec) },
        Option { () ⇒
          ServiceDiscovery(system).subscribe(gateway)
          system.log.info(s"\n★ ★ ★  [$name] was started on $httpPrefixAddress  ★ ★ ★")
        },
        Option(() ⇒ system.log.info(s"\n★ ★ ★  [$name] was stopped on $httpPrefixAddress  ★ ★ ★"))
      )

  //TODO: try to avoid timeout
  private def gatewayRoute(implicit ec: ExecutionContext): Route =
    pathPrefix(pathPref) {
      path(Segments) { path ⇒
        ctx ⇒
          ctx.complete {
            gateway.ask(ctx.request)
              .mapTo[HttpResponse]
              .recover {
                case ex: Exception ⇒
                  HttpResponse(InternalServerError, List(Host(HostHeader(externalAddress), httpPort)), ex.getMessage)
              }
          }
      }
    } ~ path("routes") {
      get { ctx =>
        import DiscoveryMicroservice._
        import HystrixMetricsMicroservice._
        ctx.complete {
          List(
            curl("GET", s"$hystrixStream"),
            curl("GET", s"$pathPref/crawler"),
            curl("GET", s"$servicePrefix/$scalarResponse"),
            curl("GET", s"$servicePrefix/$streamResponse"),
            curl("""POST -d '{"key":"api.results","value":"111"}' -H "Content-Type:application/json" """, servicePrefix),
            curl("""PUT -d '{"key":"api.results","value":"111"}' -H "Content-Type:application/json" """, servicePrefix),
            curl("DELETE", s"$servicePrefix/akka.tcp://SportCenter@192.168.0.62:3561")
          ).toJson.prettyPrint
        }
      }
    }

  /**
   * curl http://192.168.0.143:9001/discovery/scalar
   * curl http://192.168.0.143:9001/discovery/stream
   * curl http://192.168.0.143:9001/discovery/hystrix/stream
   * curl -i -X DELETE http://192.168.0.143:9005/discovery/akka.tcp://SportCenter@192.168.0.62:3561
   * curl -i -X POST -d '{"key":"akka.tcp://SportCenter@192.168.0.62:3561","value":"6876"}' -H "Content-Type:application/json" http://192.168.0.143:9001/discovery
   * curl -i -X PUT -d '{"key":"akka.tcp://SportCenter@192.168.0.62:3561","value":"6876"}' -H "Content-Type:application/json" http://192.168.0.143:9015/discovery
   */
}

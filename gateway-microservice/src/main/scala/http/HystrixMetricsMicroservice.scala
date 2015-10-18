package http

import akka.stream.actor.ActorPublisher
import akka.stream.scaladsl.Source
import hystrix.HystrixMetricsPublisher
import microservice.SystemSettings
import microservice.http.RestApiJunction
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import microservice.api.{ BootableMicroservice, ClusterNetworkSupport }
import akka.http.scaladsl.server.Route

import scala.concurrent.ExecutionContext

object HystrixMetricsMicroservice {
  private val prefix = "hystrix"
  private val stream = "stream"

  val hystrixStream = s"/$prefix/$stream"
  val dispatcher = "hystrix-stream-dispatcher"
}

trait HystrixMetricsMicroservice extends DiscoveryMicroservice
    with SSEventsMarshalling {
  mixin: ClusterNetworkSupport with BootableMicroservice ⇒
  import http.HystrixMetricsMicroservice._

  abstract override def configureApi() =
    super.configureApi() ~
      RestApiJunction(Option { ec: ExecutionContext ⇒ metricsStreamRoute(ec) })

  private def metricsPublisher() = system.actorOf(HystrixMetricsPublisher.props.withDispatcher(dispatcher))

  private def metricsStreamRoute(implicit ec: ExecutionContext): Route = {
    path(prefix / stream) {
      get {
        complete {
          ToResponseMarshallable(Source(ActorPublisher[Vector[String]](metricsPublisher())))(messageToResponseMarshaller)
        }
      }
    }
  }
}
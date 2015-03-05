package services.hystrix

import akka.http.server.Route
import microservice.http.RestApiJunction
import akka.stream.scaladsl.Source
import akka.stream.actor.ActorPublisher
import akka.http.marshalling.ToResponseMarshallable
import microservice.api.{ BootableMicroservice, ClusterNetworkSupport }
import services.discovery.{ HystrixMetricsPublisher, DiscoveryMicroservice }

import scala.concurrent.ExecutionContext

object HystrixMetricsMicroservice {
  private val prefix = "hystrix"
  private val stream = "stream"

  val hystrixStream = s"/$prefix/$stream"

  private val dispatcher = "hystrix-stream-dispatcher"
}

trait HystrixMetricsMicroservice extends DiscoveryMicroservice {
  mixin: ClusterNetworkSupport with BootableMicroservice ⇒
  import services._
  import HystrixMetricsMicroservice._

  /**
   *
   * @return
   */
  abstract override def configureApi() =
    super.configureApi() ~
      RestApiJunction(Option { ec: ExecutionContext ⇒ metricsStreamRoute(ec) })

  private def metricsPublisher() = system.actorOf(HystrixMetricsPublisher.props.withDispatcher(dispatcher))

  private def metricsStreamRoute(implicit ec: ExecutionContext): Route =
    path(prefix / stream) {
      get {
        complete {
          ToResponseMarshallable(Source(ActorPublisher[Vector[String]](metricsPublisher())))(messageToResponseMarshaller)
        }
      }
    }
}
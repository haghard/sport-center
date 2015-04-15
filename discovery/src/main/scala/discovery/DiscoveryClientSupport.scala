package discovery

import akka.actor.Address
import akka.http.model.{ StatusCode, StatusCodes }
import akka.pattern.{ AskTimeoutException, ask }
import microservice.ClusterMonitor
import microservice.ClusterMonitor.GetNodes
import microservice.api.{ MicroserviceKernel, BootableMicroservice }
import microservice.http.RestWithDiscovery

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.util.{ Failure, Success }
import scalaz.{ -\/, \/, \/- }

trait DiscoveryClientSupport extends BootableMicroservice {
  self: RestWithDiscovery with DiscoveryClient ⇒

  private val cluster = akka.cluster.Cluster(system)

  val discoveryDispatcherName = "scheduler-dispatcher"
  private implicit val discoveryDispatcher = system.dispatchers.lookup(discoveryDispatcherName)

  private val duration = 3 seconds
  private implicit val discoveryTimeout = akka.util.Timeout(duration)

  private val clusterMonitor =
    system.actorOf(ClusterMonitor.props(Option(MicroserviceKernel.GatewayRole)),
      name = "cluster-monitor")

  protected def askForDiscoveryNodeAddresses(): Future[String \/ Vector[Address]] =
    clusterMonitor
      .ask(GetNodes)(discoveryTimeout)
      .mapTo[Vector[Address]]
      .map(\/-(_))
      .recoverWith {
        case ex: AskTimeoutException ⇒ Future.successful(-\/(s"Fetch discovery nodes addresses timeout ${ex.getMessage}"))
        case ex: Exception           ⇒ Future.successful(-\/(s"Fetch discovery nodes addresses error ${ex.getMessage}"))
      }

  private def registerSequence(endpoints: List[String])(op: (String, String) ⇒ Future[StatusCode]): Unit = {
    endpoints match {
      case Nil ⇒
      case endpoint :: tail ⇒
        op(key, endpoint).onComplete {
          case Success(_) ⇒
            system.log.info(s"Service [$key - $endpoint] was successfully registered")
            registerSequence(tail)(op)
          case Failure(ex) ⇒
            system.log.info(s"Service [$key - $endpoint] installation error: ${ex.getMessage}")
        }
    }
  }

  abstract override def startup() = {
    cluster.registerOnMemberUp {
      registerSequence(endpoints)(set)
    }
    super.startup()
  }

  private def blockingCleanup: Future[Vector[StatusCode]] = {
    var statusCodes = Vector[StatusCode]()
    for { endpoint ← endpoints } {
      val resp = Await.result(delete(key, endpoint), duration)
      resp match {
        case StatusCodes.OK ⇒
          system.log.info(s"Service [$key - $endpoint] was successfully unregistered")
        case other ⇒
          system.log.info(s"Service [$key - $endpoint] unregistered error $other")
      }
      statusCodes = statusCodes :+ resp
    }
    Future.successful(statusCodes)
  }

  private def unregister = Await.result(delete(key), duration)

  private def unregisterSequence =
    Await.result(blockingCleanup, (duration * endpoints.size))

  abstract override def shutdown() = {
    unregisterSequence
    cluster.leave(cluster.selfAddress)
    super.shutdown()
  }
}
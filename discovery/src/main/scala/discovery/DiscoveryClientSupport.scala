package discovery

import akka.actor.Address
import microservice.ClusterMonitor
import microservice.ClusterMonitor.GetHttpNodes
import akka.pattern.{ AskTimeoutException, ask }
import microservice.http.ShardedDomainReadService
import akka.http.scaladsl.model.{ StatusCodes, StatusCode }
import microservice.api.{ MicroserviceKernel, BootableMicroservice }

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.util.{ Failure, Success }
import scalaz.{ -\/, \/, \/- }

trait DiscoveryClientSupport extends BootableMicroservice {
  self: ShardedDomainReadService with DiscoveryClient ⇒
  val duration = 5 seconds
  private val cluster = akka.cluster.Cluster(system)
  val discoveryDispatcherName = "scheduler-dispatcher"
  private implicit val discoveryTimeout = akka.util.Timeout(duration)
  private implicit val discoveryDispatcher = system.dispatchers.lookup(discoveryDispatcherName)

  private val clusterMonitor = system.actorOf(
    ClusterMonitor.props(Option(MicroserviceKernel.GatewayRole)), "cluster-monitor")

  protected def askForDiscoveryNodeAddresses(): Future[String \/ Vector[Address]] =
    clusterMonitor
      .ask(GetHttpNodes)(discoveryTimeout)
      .mapTo[Vector[Address]]
      .map(\/-(_))(discoveryDispatcher)
      .recoverWith {
        case ex: AskTimeoutException ⇒ Future.successful(-\/(s"Fetch discovery nodes addresses timeout ${ex.getMessage}"))
        case ex: Exception           ⇒ Future.successful(-\/(s"Fetch discovery nodes addresses error ${ex.getMessage}"))
      }(discoveryDispatcher)

  private def registerMyself(endpoints: List[String])(op: (String, String) ⇒ Future[StatusCode]): Unit = {
    endpoints match {
      case Nil ⇒
      case endpoint :: tail ⇒
        op(key, endpoint).onComplete {
          case Success(_) ⇒
            system.log.info(
              new StringBuilder().append("\n").append(s"★ ★ ★ Microservice [$key - $endpoint] was successfully registered")
                .toString)
            registerMyself(tail)(op)
          case Failure(ex) ⇒
            system.log.info(
              new StringBuilder().append("\n")
                .append(s"★ ★ ★ Microservice [$key - $endpoint] registration error").toString)
            throw new Exception("Registration hasn't been competed")
        }(discoveryDispatcher)
    }
  }

  abstract override def startup() = {
    cluster.registerOnMemberUp {
      system.log.info(new StringBuilder().append("\n")
        .append(s"★ ★ ★ Microservice endpoints [${endpoints.mkString("\t")}] ★ ★ ★")
        .toString)
      registerMyself(endpoints)(set)
    }
    super.startup()
  }

  private def cleanup: Future[Vector[StatusCode]] = {
    var statusCodes = Vector[StatusCode]()
    for { endpoint ← endpoints } {
      val resp = Await.result(delete(key, endpoint), duration)
      resp match {
        case StatusCodes.OK ⇒ system.log.info(
          new StringBuilder().append("\n")
            .append(s"★ ★ ★ Microservice [$key - $endpoint] was successfully unregistered ★ ★ ★")
            .toString)

        case statusCode ⇒ system.log.info(
          new StringBuilder().append("\n")
            .append(s"★ ★ ★ Microservice [$key - $endpoint] unregistered error $statusCode registered")
            .toString)
      }
      statusCodes = statusCodes :+ resp
    }
    Future.successful(statusCodes)
  }

  private def unregisterMyself = Await.result(cleanup, (duration * endpoints.size))

  abstract override def shutdown() = {
    unregisterMyself
    cluster.leave(cluster.selfAddress)
    super.shutdown()
  }
}
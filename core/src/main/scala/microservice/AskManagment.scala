package microservice

import akka.actor.ActorRef
import microservice.domain.State
import akka.pattern.{ AskTimeoutException, ask }
import scala.concurrent.{ ExecutionContext, Future }
import microservice.http.RestService.BasicHttpRequest

import scala.reflect.ClassTag
import scalaz.{ -\/, \/, \/- }

trait AskManagment {

  def fetch[T <: State](message: BasicHttpRequest, target: ActorRef)(implicit ec: ExecutionContext, fetchTimeout: akka.util.Timeout, tag: ClassTag[T]): Future[String \/ T] =
    target
      .ask(message)
      .mapTo[T]
      .map(\/-(_))
      .recoverWith {
        case ex: ClassCastException  => Future.successful(-\/(ex.getMessage))
        case ex: AskTimeoutException => Future.successful(-\/(s"Fetch results operation timeout ${ex.getMessage}"))
      }
}
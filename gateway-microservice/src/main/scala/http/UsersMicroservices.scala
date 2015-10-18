package http

import akka.http.scaladsl.server._
import http.UsersMicroservices.UserProtocols
import microservice.SystemSettings
import microservice.api.BootableMicroservice
import microservice.http.{ RestApiJunction, BootableRestService, Session }
import com.softwaremill.session.SessionDirectives._
import spray.json.DefaultJsonProtocol
import scala.concurrent.ExecutionContext

object UsersMicroservices {
  case class RawUser(login: String, email: String)
  trait UserProtocols extends DefaultJsonProtocol {
    implicit val user = jsonFormat2(RawUser.apply)
  }
}

trait UsersMicroservices extends BootableRestService with SystemSettings
    with UserProtocols { mixin: Directives with BootableMicroservice ⇒
  import UsersMicroservices._

  implicit val ec = system.dispatchers.lookup(httpDispatcher)

  abstract override def configureApi() =
    super.configureApi() ~ RestApiJunction(route = Option({ ec: ExecutionContext ⇒ authRoute(ec) }))

  private def authRoute(implicit ex: ExecutionContext): Route = {
    randomTokenCsrfProtection() {
      pathPrefix(pathPref) {
        (get & path("login")) {
          parameters('user.as[String], 'email.as[String]).as(RawUser) { rawUser ⇒
            val u = s"${rawUser.login} ${microservice.http.User.encryptPassword(rawUser.email, sail)}"
            setPersistentSession(Session(u)) { ctx => ctx.complete(s"${rawUser.login} has been logged in") }
          }
        } /* ~ (get & path("logout")) {
          requiredPersistentSession() { session =>
            invalidatePersistentSession() { ctx =>
              ctx.complete(s"${session} has been logout")
            }
          }
        }*/
      }
    }
  }
}
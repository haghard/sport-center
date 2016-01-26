package http

import akka.http.scaladsl.server._
import http.UsersMicroservices.UserProtocols
import microservice.SystemSettings
import microservice.api.BootableMicroservice
import microservice.http.{ RestApiJunction, BootableRestService, Session }
import com.softwaremill.session.SessionDirectives._
import spray.json.DefaultJsonProtocol
import scala.concurrent.ExecutionContext
import UsersMicroservices._

object UsersMicroservices {
  case class RawUser(login: String, email: String)
  trait UserProtocols extends DefaultJsonProtocol {
    implicit val user = jsonFormat2(RawUser.apply)
  }
}

trait UsersMicroservices extends BootableRestService with SystemSettings
    with UserProtocols { mixin: Directives with BootableMicroservice ⇒

  import com.softwaremill.session.SessionOptions._

  implicit val ec = system.dispatchers.lookup(httpDispatcher)

  abstract override def configureApi() =
    super.configureApi() ~ RestApiJunction(route = Option({ ec: ExecutionContext ⇒ loginRoute(ec) }))

  private def loginRoute(implicit ex: ExecutionContext): Route = {
    pathPrefix(pathPref) {
      path("login") {
        get {
          parameters('user.as[String], 'email.as[String]).as(RawUser) { rawUser ⇒
            val u = s"${rawUser.login}:${microservice.http.User.encryptPassword(rawUser.email, salt)}"
            setSession(refreshable, usingCookies, Session(u)) {
              complete(s"${rawUser.login} was logged in")
            }
          }
        }
      }
    }
  }
}
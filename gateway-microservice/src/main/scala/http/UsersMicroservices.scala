package http

import akka.http.scaladsl.server._
import com.softwaremill.session.CsrfDirectives._
import com.softwaremill.session.CsrfOptions._
import http.UsersMicroservices.UserProtocols
import microservice.SystemSettings
import microservice.api.BootableMicroservice
import microservice.http.BootableRestService.ServerSession
import microservice.http.{ RestApiJunction, BootableRestService }
import com.softwaremill.session.SessionDirectives._
import org.mindrot.jbcrypt.BCrypt
import spray.json.DefaultJsonProtocol
import scala.concurrent.ExecutionContext
import UsersMicroservices._

object UsersMicroservices {
  case class RawUser(login: String, password: String)
  trait UserProtocols extends DefaultJsonProtocol {
    implicit val user = jsonFormat2(RawUser.apply)
  }
}

trait UsersMicroservices extends BootableRestService with SystemSettings
    with UserProtocols { mixin: Directives with BootableMicroservice ⇒

  import com.softwaremill.session.SessionOptions._

  implicit val ec = system.dispatchers.lookup(httpDispatcher)

  val salt = org.mindrot.jbcrypt.BCrypt.gensalt()

  abstract override def configureApi() =
    super.configureApi() ~ RestApiJunction(route = Option({ ec: ExecutionContext ⇒ loginRoute(ec) }))

  private def loginRoute(implicit ex: ExecutionContext): Route = {
    pathPrefix(pathPref) {
      path("login") {
        get {
          parameters('user.as[String], 'password.as[String]).as(RawUser) { rawUser ⇒
            setSession(oneOff, usingHeaders, ServerSession(rawUser.login, org.mindrot.jbcrypt.BCrypt.hashpw(rawUser.password, salt))) {
              setNewCsrfToken(checkHeader) { ctx ⇒ ctx.complete(s"welcome ${rawUser.login}") }
            }
          }
        }
      }
    }
  }
}
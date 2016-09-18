package microservice.http

import akka.actor.ActorSystem
import akka.http.scaladsl.server._
import com.softwaremill.session.SessionDirectives._
import microservice.SystemSettings
import microservice.api.BootableMicroservice
import microservice.api.MicroserviceKernel._
import com.softwaremill.session._
import com.softwaremill.session.SessionOptions._

import scala.concurrent.ExecutionContext
import scala.util.Try
import BootableRestService._

object BootableRestService {
  case class ServerSession(user: String, password: String)
}

trait BootableRestService extends SystemSettings with Directives {
  mixin: BootableMicroservice =>

  def name: String

  val pathPref = "api"

  def httpPrefixAddress: String

  def configureApi() = RestApiJunction()

  val httpDispatcher = microserviceDispatcher

  def withUri: Directive1[String] = extract(_.request.uri.toString())

  def installApi(api: RestApiJunction)(implicit system: ActorSystem, interface: String, httpPort: Int) = {
    api.route.foreach { api =>
      val ec = system.dispatchers.lookup(httpDispatcher)
      val route = api(ec)
      system.actorOf(RestService.props(route, interface, httpPort)(ec), "rest-service")
    }
    api.preAction.foreach(action => action())
  }

  def uninstallApi(api: RestApiJunction) = api.postAction.foreach(action => action())

  implicit val sessionManager = new SessionManager[ServerSession](SessionConfig.default(system.settings.config.getString("akka.http.session.server-secret")))

  implicit def serializer: SessionSerializer[ServerSession, String] =
    new SingleValueSessionSerializer({ session: ServerSession ⇒ (session.user + "-" + session.password) }, { v: (String) ⇒
      val kv = v.split("-")
      Try(ServerSession(kv(0), kv(1)))
    })

  implicit val refreshTokenStorage = new InMemoryRefreshTokenStorage[ServerSession] {
    def log(msg: String) = system.log.info("Refresh token {}", msg)
  }

  //oneOff vs refreshable; specifies what should happen when the session expires.
  //If refreshable and a refresh token is present, the session will be re-created
  def requiredHttpSession(implicit ec: ExecutionContext) = requiredSession(oneOff, usingHeaders)
}
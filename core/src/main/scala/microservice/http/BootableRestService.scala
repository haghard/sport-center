package microservice.http

import akka.actor.ActorSystem
import akka.http.scaladsl.server._
import com.softwaremill.session.SessionDirectives._
import microservice.SystemSettings
import microservice.api.BootableMicroservice
import microservice.api.MicroserviceKernel._
import com.softwaremill.session._

import scala.concurrent.ExecutionContext

trait BootableRestService extends SystemSettings with Directives {
  mixin: BootableMicroservice =>

  import com.softwaremill.session.SessionOptions._

  def name: String

  val pathPref = "api"

  def httpPrefixAddress: String

  def configureApi() = RestApiJunction()

  val httpDispatcher = microserviceDispatcher

  def salt = settings.session.sail

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

  implicit val sessionManager = new SessionManager[Session](SessionConfig.default(settings.session.secret))

  implicit val refreshTokenStorage = new InMemoryRefreshTokenStorage[Session] {
    def log(msg: String) = system.log.info("Refresh token {}", msg)
  }

  def requiredHttpSession(implicit ec: ExecutionContext) = requiredSession(refreshable, usingCookies)
}
package microservice.http

import akka.actor.ActorSystem
import akka.http.scaladsl.server._
import microservice.SystemSettings
import microservice.api.BootableMicroservice
import microservice.api.MicroserviceKernel._
import com.softwaremill.session.{ RememberMeStorage, InMemoryRememberMeStorage, SessionManager, SessionConfig }

trait BootableRestService extends SystemSettings with Directives {
  mixin: BootableMicroservice =>

  def name: String

  val pathPref = "api"

  def httpPrefixAddress: String

  def configureApi() = RestApiJunction()

  val httpDispatcher = microserviceDispatcher

  def installApi(api: RestApiJunction)(implicit system: ActorSystem, interface: String, httpPort: Int) = {
    api.route.foreach { api =>
      val ec = system.dispatchers.lookup(httpDispatcher)
      val route = api(ec)
      system.actorOf(RestService.props(route, interface, httpPort)(ec), "rest-service")
    }

    api.preAction.foreach(action => action())
  }

  def uninstallApi(api: RestApiJunction) = api.postAction.foreach(action => action())

  implicit val sessionManager = new SessionManager[Session](SessionConfig.default(settings.session.secret)
    .withClientSessionEncryptData(true)
    .withClientSessionMaxAgeSeconds(Option(settings.session.ttl))
    .withRememberMeCookieMaxAge(Option(settings.session.ttl)))

  implicit val rememberMeStorage: RememberMeStorage[Session] = new InMemoryRememberMeStorage[Session] {
    override def log(msg: String) = system.log.info("remember-me {}", msg)
  }

  def sail = settings.session.sail
}
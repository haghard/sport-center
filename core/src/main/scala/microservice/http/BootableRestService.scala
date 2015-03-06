package microservice.http

import akka.actor.ActorSystem
import microservice.api.MicroserviceKernel._

trait BootableRestService {

  protected def name: String

  protected val pathPrefix = "api"

  protected def httpPrefixAddress: String

  protected def configureApi() = RestApiJunction()

  protected val httpDispatcher = microserviceDispatcher

  protected def installApi(api: RestApiJunction)(implicit system: ActorSystem, interface: String, httpPort: Int) = {
    api.route.foreach { api =>
      val ec = system.dispatchers.lookup(httpDispatcher)
      val route = api(ec)
      system.actorOf(RestService.props(route, interface, httpPort)(ec), "rest-service")
    }

    api.preAction.foreach(action => action())
  }

  protected def uninstallApi(api: RestApiJunction) = api.postAction.foreach(action => action())
}
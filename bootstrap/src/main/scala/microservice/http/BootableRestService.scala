package microservice.http

import akka.http.server._
import akka.actor.ActorSystem
import microservice.api.MicroserviceKernel
import microservice.api.MicroserviceKernel._

trait BootableRestService {

  protected val pathPrefix = "api"

  protected val httpDispatcher = microserviceDispatcher

  protected def name: String

  protected def httpPrefixAddress: String

  protected def configureApi() = RestApi()

  protected def installApi(api: RestApi)(implicit system: ActorSystem, interface: String, httpPort: Int) = {
    api.route.foreach { api =>
      val ec = system.dispatchers.lookup(httpDispatcher)
      val route = api(ec)
      system.actorOf(RestService.props(route, interface, httpPort)(ec), "http-rest-api")
    }

    api.preAction.foreach(action => action())
  }

  protected def uninstallApi(api: RestApi) = api.postAction.foreach(action => action())
}
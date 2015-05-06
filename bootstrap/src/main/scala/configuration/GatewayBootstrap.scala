package configuration

import configuration.Microservices._
import configuration.Microservices.container._

object GatewayBootstrap extends SystemPropsSupport with App {
  implicit val defaultAkkaPort = "2551"
  implicit val defaultHttpPort = 2561

  if (args.size > 0)
    applySystemProperties(args)

  implicit val cfg = RouterCfg(
    Option(System.getProperty(configuration.AKKA_PORT_VAR)).getOrElse(defaultAkkaPort),
    Option(System.getProperty(configuration.HTTP_PORT_VAR)).map(_.toInt).getOrElse(defaultHttpPort),
    randomJmxPort, "Gateway-Registry")

  microservice[RouterCfg].startup()
}
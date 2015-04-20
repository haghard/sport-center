package configuration

import configuration.Microservices._
import configuration.Microservices.local._

object GatewayBootstrap extends SystemPropsSupport {
  implicit val defaultAkkaPort = "2551"
  implicit val defaultHttpPort = 2561

  def main(args: Array[String]) = {
    if (args.size > 0)
      applySystemProperties(args)

    implicit val cfg = RouterCfg(
      Option(System.getProperty(configuration.AKKA_PORT_VAR)).getOrElse(defaultAkkaPort),
      Option(System.getProperty(configuration.HTTP_PORT_VAR)).map(_.toInt).getOrElse(defaultHttpPort),
      randomJmxPort, "Gateway-Registry")

    microservice[RouterCfg].startup()
  }
}
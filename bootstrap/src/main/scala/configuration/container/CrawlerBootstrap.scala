package configuration.container

import configuration.SystemPropsSupport

object CrawlerBootstrap extends SystemPropsSupport with App {
  import GatewayBootstrap._
  import configuration.Microservices._
  import configuration.Microservices.container._

  if (!args.isEmpty)
    applySystemProperties(args)

  implicit val cfg = CrawlerCfg(
    Option(System.getProperty(configuration.AKKA_PORT_VAR)).getOrElse(defaultAkkaPort),
    Option(System.getProperty(configuration.HTTP_PORT_VAR)).map(_.toInt).getOrElse(defaultHttpPort),
    randomJmxPort, "Crawler")

  val node = microservice[CrawlerCfg]
  node.startup()

  Runtime.getRuntime.addShutdownHook(new Thread(() ⇒ node.shutdown))
}
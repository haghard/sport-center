package configuration.container

object GatewayBootstrap extends configuration.SystemPropsSupport with App {
  import configuration.Microservices._
  import configuration.Microservices.container._

  implicit val defaultAkkaPort = "2551"
  implicit val defaultHttpPort = 2561

  if (args.size > 0)
    applySystemProperties(args)

  implicit val cfg = GatewayCfg(
    Option(System.getProperty(configuration.AKKA_PORT_VAR)).getOrElse(defaultAkkaPort),
    Option(System.getProperty(configuration.HTTP_PORT_VAR)).map(_.toInt).getOrElse(defaultHttpPort),
    randomJmxPort, "Gateway")

  microservice[GatewayCfg].startup()
}
package configuration.container

object QueryStandingBootstrap extends configuration.SystemPropsSupport with App {
  import GatewayBootstrap._
  import configuration.Microservices._
  import configuration.Microservices.container._

  if (!args.isEmpty)
    applySystemProperties(args)

  implicit val cfg = StandingCfg(
    Option(System.getProperty(configuration.AKKA_PORT_VAR)).getOrElse(defaultAkkaPort),
    Option(System.getProperty(configuration.HTTP_PORT_VAR)).map(_.toInt).getOrElse(defaultHttpPort),
    randomJmxPort, "Query-standing")

  val node = microservice[StandingCfg]
  node.startup()

  Runtime.getRuntime.addShutdownHook(new Thread(() ⇒ node.shutdown))
}

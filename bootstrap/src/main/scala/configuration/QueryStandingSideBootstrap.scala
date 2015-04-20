package configuration

object QueryStandingSideBootstrap extends SystemPropsSupport {

  import GatewayBootstrap._
  def main(args: Array[String]) = {
    if (!args.isEmpty)
      applySystemProperties(args)

    import configuration.Microservices._
    import configuration.Microservices.local._

    implicit val cfg = StandingCfg(
      Option(System.getProperty(configuration.AKKA_PORT_VAR)).getOrElse(defaultAkkaPort),
      Option(System.getProperty(configuration.HTTP_PORT_VAR)).map(_.toInt).getOrElse(defaultHttpPort),
      randomJmxPort, "Query-side-standing")

    val node = microservice[StandingCfg]
    node.startup()

    Runtime.getRuntime.addShutdownHook(new Thread(() ⇒ node.shutdown))
  }
}

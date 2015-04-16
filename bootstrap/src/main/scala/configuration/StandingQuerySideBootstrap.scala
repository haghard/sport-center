package configuration

object StandingQuerySideBootstrap extends SystemPropsSupport {

  import GatewayBootstrap._
  def main(args: Array[String]) = {
    if (!args.isEmpty)
      applySystemProperties(args)

    import configuration.Microservices._
    import configuration.Microservices.local._

    implicit val cfg = StandingCfg(
      Option(System.getProperty(configuration.AKKA_PORT)).getOrElse(defaultAkkaPort),
      Option(System.getProperty(configuration.HTTP_PORT)).map(_.toInt).getOrElse(defaultHttpPort),
      randomJmxPort, "[Local]-Standing")

    val node = microservice[StandingCfg]
    node.startup()

    Runtime.getRuntime.addShutdownHook(new Thread(() ⇒ node.shutdown))
  }
}

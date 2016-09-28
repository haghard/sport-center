package configuration.local

object QueryStandingBootstrap extends configuration.SystemPropsSupport with App {
  import configuration.Microservices._
  import configuration.Microservices.local._

  private val defaultAkkaPort = "2557"
  private val defaultHttpPort = 9012

  if (!args.isEmpty)
    applySystemProperties(args)

  implicit val cfg = StandingCfg(
    Option(System.getProperty(configuration.AKKA_PORT_VAR)).getOrElse(defaultAkkaPort),
    Option(System.getProperty(configuration.HTTP_PORT_VAR)).map(_.toInt).getOrElse(defaultHttpPort),
    randomJmxPort, "Query-standing"
  )

  val node = microservice[StandingCfg]
  node.startup()

  Runtime.getRuntime.addShutdownHook(new Thread(() ⇒ node.shutdown))
}

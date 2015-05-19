package configuration.local

object AnalyticsBootstrap extends configuration.SystemPropsSupport with App {

  import configuration.Microservices._
  import configuration.Microservices.local._

  private val defaultAkkaPort = "2559"
  private val defaultHttpPort = 9014

  if (!args.isEmpty) {
    applySystemProperties(args)
  }

  implicit val cfg = AnalyticsCfg(
    Option(System.getProperty(configuration.AKKA_PORT_VAR)).getOrElse(defaultAkkaPort),
    Option(System.getProperty(configuration.HTTP_PORT_VAR)).map(_.toInt).getOrElse(defaultHttpPort),
    randomJmxPort,
    "Analytics")

  val node = microservice[AnalyticsCfg]
  node.startup()

  Runtime.getRuntime.addShutdownHook(new Thread(() ⇒ node.shutdown))
}

package configuration.local

object CrawlerBootstrap extends configuration.SystemPropsSupport with App {
  import configuration.Microservices._
  import configuration.Microservices.local._

  private val defaultAkkaPort = "3551"
  private val defaultHttpPort = 9007

  if (!args.isEmpty)
    applySystemProperties(args)

  implicit val cfg = CrawlerCfg(
    Option(System.getProperty(configuration.AKKA_PORT_VAR)).getOrElse(defaultAkkaPort),
    Option(System.getProperty(configuration.HTTP_PORT_VAR)).map(_.toInt).getOrElse(defaultHttpPort),
    randomJmxPort,
    "Crawler")

  val node = microservice[CrawlerCfg]
  node.startup()

  Runtime.getRuntime.addShutdownHook(new Thread(() ⇒ node.shutdown))
}
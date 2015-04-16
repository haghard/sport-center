package configuration


object BootstrapCrawler extends SystemPropsSupport {

  def main(args: Array[String]) = {
    if (!args.isEmpty)
      applySystemProperties(args)

    import configuration.Microservices._
    import configuration.Microservices.local._
    import GatewayBootstrap._

    implicit val cfg = CrawlerCfg(
      Option(System.getProperty(configuration.AKKA_PORT)).getOrElse(defaultAkkaPort),
      Option(System.getProperty(configuration.HTTP_PORT)).map(_.toInt).getOrElse(defaultHttpPort),
      randomJmxPort, "Crawler")

    val node = microservice[CrawlerCfg]
    node.startup()

    /*
    import configuration.ClusterConfigurations
    import configuration.ClusterConfigurations.Crawler
    import configuration.ClusterConfigurations.local._

    val node = ClusterConfigurations.node[Crawler]
    node.startup()
    */

    Runtime.getRuntime.addShutdownHook(new Thread(() ⇒ node.shutdown))
  }
}
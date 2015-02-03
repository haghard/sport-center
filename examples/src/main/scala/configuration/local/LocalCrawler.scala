package configuration.local

import configuration.SystemPropsSupport

object LocalCrawler extends SystemPropsSupport {

  def main(args: Array[String]) = {
    implicit var akkaPort = "2551"
    if (!args.isEmpty) {
      applySystemProperties(args)
      akkaPort = args(0)
    }

    import configuration.Microservices._
    import configuration.Microservices.local._

    implicit val cfg = CrawlerCfg(akkaPort,
      Option(System.getProperty("http.port").toInt).getOrElse(randomHttpPort),
      randomJmxPort, "[Local]-Crawler")

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
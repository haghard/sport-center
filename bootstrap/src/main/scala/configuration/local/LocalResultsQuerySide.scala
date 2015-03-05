package configuration.local

import configuration.SystemPropsSupport
import configuration.Microservices._
import configuration.Microservices.local._

object LocalResultsQuerySide extends SystemPropsSupport {

  def main(args: Array[String]) = {
    implicit var akkaPort = "2551"

    if (!args.isEmpty) {
      applySystemProperties(args)
      akkaPort = args(0)
    }

    implicit val cfg = ResultsQuerySideCfg(akkaPort,
      Option(System.getProperty("http.port").toInt).getOrElse(randomHttpPort),
      randomJmxPort, "[Local]-ResultsQuerySide")

    val node = microservice[ResultsQuerySideCfg]
    node.startup()

    Runtime.getRuntime.addShutdownHook(new Thread(() ⇒ node.shutdown))

    /*import configuration.ClusterConfigurations
    import configuration.ClusterConfigurations.Results

    val node = ClusterConfigurations.node[Results]
    node.startup()
    */
  }
}
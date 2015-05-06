package configuration

import configuration.Microservices._
import configuration.Microservices.container._

object QueryResultsSideBootstrap extends SystemPropsSupport with App {

  import GatewayBootstrap._

  if (!args.isEmpty)
    applySystemProperties(args)

  implicit val cfg = ResultsQuerySideCfg(
    Option(System.getProperty(configuration.AKKA_PORT_VAR)).getOrElse(defaultAkkaPort),
    Option(System.getProperty(configuration.HTTP_PORT_VAR)).map(_.toInt).getOrElse(defaultHttpPort),
    randomJmxPort, "Query-side-results")

  val node = microservice[ResultsQuerySideCfg]
  node.startup()

  Runtime.getRuntime.addShutdownHook(new Thread(() ⇒ node.shutdown))
}
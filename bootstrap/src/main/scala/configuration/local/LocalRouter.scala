package configuration.local

import configuration.SystemPropsSupport
import configuration.Microservices._
import configuration.Microservices.local._

object LocalRouter extends SystemPropsSupport {

  def main(args: Array[String]) = {
    implicit var akkaPort = "2551"

    if (!args.isEmpty) {
      applySystemProperties(args)
      akkaPort = args(0)
    }

    implicit val cfg = RouterCfg(akkaPort,
      Option(System.getProperty("http.port").toInt).getOrElse(randomHttpPort),
      randomJmxPort, "[Local]-Router-Registry")

    microservice[RouterCfg].startup()
  }
}

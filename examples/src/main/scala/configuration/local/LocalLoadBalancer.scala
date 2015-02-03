package configuration.local

import configuration.SystemPropsSupport
import configuration.Microservices._
import configuration.Microservices.local._

object LocalLoadBalancer extends SystemPropsSupport {

  def main(args: Array[String]) = {
    implicit var akkaPort = "2551"

    if (!args.isEmpty) {
      applySystemProperties(args)
      akkaPort = args(0)
    }

    implicit val cfg = LoadBalancerCfg(akkaPort,
      Option(System.getProperty("http.port").toInt).getOrElse(randomHttpPort),
      randomJmxPort, "[Local]-LoadBalancer-Discovery")

    microservice[LoadBalancerCfg].startup()
  }
}

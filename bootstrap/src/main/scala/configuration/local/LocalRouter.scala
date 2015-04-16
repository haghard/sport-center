package configuration.local

import configuration.SystemPropsSupport
import configuration.Microservices._
import configuration.Microservices.local._

import scala.util.Try

object LocalRouter extends SystemPropsSupport {
  implicit var akkaPort = "2551"

  def main(args: Array[String]) = {
    if (args.size > 0) {
      args.foreach(println)
      applySystemProperties(args)
      akkaPort = args(0)
    }

    println("HOST_IP0: " + System.getProperty("HOST_IP0"))

    implicit val cfg = RouterCfg(akkaPort,
      Try(System.getProperty("HTTP_PORT").toInt).getOrElse(randomHttpPort),
      randomJmxPort, "[Local]-Router-Registry")

    microservice[RouterCfg].startup()
  }
}
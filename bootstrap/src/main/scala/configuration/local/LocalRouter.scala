package configuration.local

import configuration.SystemPropsSupport
import configuration.Microservices._
import configuration.Microservices.local._

import scala.util.Try

object LocalRouter extends SystemPropsSupport {
  implicit val defaultAkkaPort = "2551"
  implicit val defaultHttpPort = 2561

  def main(args: Array[String]) = {
    if (args.size > 0) {
      //args.foreach(println)
      applySystemProperties(args)
    }

    implicit val cfg = RouterCfg(
      Option(System.getProperty("AKKA_PORT")).getOrElse(defaultAkkaPort),
      Option(System.getProperty("HTTP_PORT")).map(_.toInt).getOrElse(defaultHttpPort),
      randomJmxPort, "[Local]-Router-Registry")

    microservice[RouterCfg].startup()
  }
}
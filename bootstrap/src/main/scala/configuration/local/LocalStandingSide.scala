package configuration.local

import configuration.SystemPropsSupport

object LocalStandingSide extends SystemPropsSupport {

  def main(args: Array[String]) = {
    implicit var akkaPort = "2551"
    if (!args.isEmpty) {
      applySystemProperties(args)
      akkaPort = args(0)
    }

    import configuration.Microservices._
    import configuration.Microservices.local._

    implicit val cfg = StandingCfg(akkaPort,
      Option(System.getProperty("http.port").toInt).getOrElse(randomHttpPort),
      randomJmxPort, "[Local]-Standing")

    val node = microservice[StandingCfg]
    node.startup()

    Runtime.getRuntime.addShutdownHook(new Thread(() ⇒ node.shutdown))
  }
}

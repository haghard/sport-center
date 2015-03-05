package microservice.api

object BootableClusterNode {
  val DefaultJmxPort = 5000
  val DefaultCloudHttpPort = 9001

  val CrawlerRole = "crawler"
  val MicroserviceRole = "microservice"
  val RoutingLayerRole = "routing-layer"

  val CloudEth = "eth0"
  val LocalEth = "en0"
  val LocalEth4 = "en4"
}
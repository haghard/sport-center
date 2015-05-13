package microservice.api

object BootableClusterNode {
  val DefaultJmxPort = 5000
  val DefaultCloudHttpPort = 9001
  val CloudEth = "eth0"
  val LocalMacEth = "en0"
  val LocalEth4 = "en4"

  val SEEDS_ENV_VAR = "SEED_NODES"
}
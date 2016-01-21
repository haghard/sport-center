package microservice.api

trait ClusterNetworkSupport {

  def akkaSystemPort: String

  def ethName: String

  def externalAddress: String

  def clusterRole: String

  def httpPort: Int

  def jmxPort: Int

  def httpPrefixAddress = s"http://$externalAddress:$httpPort"

  def akkaClusterAddress = s"akka.tcp://${MicroserviceKernel.ActorSystemName}@${externalAddress}:${akkaSystemPort}"
}

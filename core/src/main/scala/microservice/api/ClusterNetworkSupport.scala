package microservice.api

trait ClusterNetworkSupport {

  def akkaSystemPort: String

  def ethName: String

  def localAddress: String

  def clusterRole: String

  def httpPort: Int

  def jmxPort: Int

  def httpPrefixAddress = s"http://$localAddress:$httpPort"

  def akkaClusterAddress = s"akka.tcp://${MicroserviceKernel.ActorSystemName}@${localAddress}:${akkaSystemPort}"
}

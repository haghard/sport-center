package microservice.api

import java.net.NetworkInterface
import scala.collection.JavaConverters._

trait LocalSeedNodesClient extends SeedNodesSupport {
  self: ClusterNetworkSupport ⇒
  import MicroserviceKernel._
  import BootableClusterNode._

  private val knownPorts = Array[Int](2551, 2552)

  override lazy val seedAddresses =
    NetworkInterface.getNetworkInterfaces.asScala.toList
      .find(_.getName == LocalEth)
      .flatMap(x ⇒ x.getInetAddresses.asScala.toList.find(_.getHostAddress.matches(ipExpression))) :: Nil flatten

  override lazy val akkaSeedNodes =
    seedAddresses.flatMap { s ⇒
      s"akka.tcp://${ActorSystemName}@${s.getHostAddress}:${knownPorts(0)}" ::
        s"akka.tcp://${ActorSystemName}@${s.getHostAddress}:${knownPorts(1)}" :: Nil
    }.asJava
}
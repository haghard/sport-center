package microservice.api

import java.net.{ InetSocketAddress, NetworkInterface }

import scala.collection.JavaConverters._

trait LocalSeedNodesClient extends SeedNodesSupport {
  self: ClusterNetworkSupport ⇒
  import MicroserviceKernel._
  import BootableClusterNode._

  private val knownPorts = Array[Int](2551, 2552)

  override def seedAddresses = {
    /*import com.typesafe.config.ConfigFactory
    val env = ConfigFactory.load("env.conf")
    val hostIp = env.getConfig("env").getString("hostIp")
    List(new InetSocketAddress(hostIp, 9999).getAddress)*/

    NetworkInterface.getNetworkInterfaces.asScala.toList
      .find(_.getName == CloudEth)
      .flatMap(x ⇒ x.getInetAddresses.asScala.toList.find(_.getHostAddress.matches(ipExpression))) :: Nil flatten
  }

  override lazy val akkaSeedNodes =
    seedAddresses.flatMap { s ⇒
      s"akka.tcp://${ActorSystemName}@${s.getHostAddress}:${knownPorts(0)}" ::
        s"akka.tcp://${ActorSystemName}@${s.getHostAddress}:${knownPorts(1)}" :: Nil
    }.asJava
}
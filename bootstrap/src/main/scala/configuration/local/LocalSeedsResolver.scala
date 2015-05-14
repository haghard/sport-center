package configuration.local

import java.net.{ InetAddress, NetworkInterface }

import microservice.api.MicroserviceKernel._
import microservice.api.{ BootableClusterNode, ClusterNetworkSupport, SeedNodesSupport }

import scala.collection.JavaConverters._

/**
 * Based on addCommandAlias("lgateway0") port 2551
 *
 */
trait LocalSeedsResolver extends SeedNodesSupport {
  self: ClusterNetworkSupport ⇒

  override def seedAddresses: Option[InetAddress] = {
    val r = NetworkInterface.getNetworkInterfaces.asScala.toList
      .find(_.getName == ethName)
      .flatMap(x ⇒ x.getInetAddresses.asScala.toList.find(i => i.getHostAddress.matches(ipExpression)))

    //based on alias command
    val seeds = r.fold("localhost:2551")(a => a.getHostAddress + ":2551")
    System.setProperty(BootableClusterNode.SEEDS_ENV_VAR, seeds)
    r
  }
}

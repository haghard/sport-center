package microservice.api

import java.net.{ InetAddress, NetworkInterface }
import scala.collection.JavaConverters._

trait SeedNodesResolver extends SeedNodesSupport {
  self: ClusterNetworkSupport ⇒

  import MicroserviceKernel._

  override def seedAddresses: Option[InetAddress] =
    NetworkInterface.getNetworkInterfaces.asScala.toList
      .find(_.getName == ethName)
      .flatMap(x ⇒ x.getInetAddresses.asScala.toList.find(i => i.getHostAddress.matches(ipExpression)))
}
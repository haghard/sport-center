package microservice.api

import java.net.NetworkInterface
import scala.collection.JavaConverters._

trait LocalSeedNodesClient extends SeedNodesSupport {
  self: ClusterNetworkSupport ⇒

  import BootableClusterNode._
  import MicroserviceKernel._

  override def seedAddresses =
    NetworkInterface.getNetworkInterfaces.asScala.toList
      .find(_.getName == CloudEth).flatMap { x ⇒
        x.getInetAddresses.asScala.toList.find { i =>
          i.getHostAddress.matches(ipExpression)
        }
      }
}
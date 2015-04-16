package microservice.api

import java.net.InetAddress

trait SeedNodesSupport {
  def seedAddresses: Option[InetAddress]
}


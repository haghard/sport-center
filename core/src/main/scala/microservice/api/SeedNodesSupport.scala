package microservice.api

import java.net.InetAddress

trait SeedNodesSupport {

  def seedAddresses: List[InetAddress]

  def akkaSeedNodes: List[String]
}


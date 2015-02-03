package microservice

import java.rmi.registry.LocateRegistry
import java.lang.management.ManagementFactory
import javax.management.remote.{ JMXConnectorServerFactory, JMXServiceURL }
import microservice.api.{ BootableMicroservice, ClusterNetworkSupport }
import scala.util.{ Failure, Success, Try }

trait JmxAgent extends BootableMicroservice {
  self: ClusterNetworkSupport ⇒

  abstract override def startup() = {
    LocateRegistry.createRegistry(jmxPort)
    val server = ManagementFactory.getPlatformMBeanServer
    val env = new java.util.HashMap[String, Object]()

    System.setProperty("java.rmi.server.hostname", localAddress)
    System.setProperty("com.sun.management.jmxremote.port", jmxPort.toString)
    System.setProperty("com.sun.management.jmxremote.authenticate", "false")
    System.setProperty("com.sun.management.jmxremote.ssl", "false")

    val address = new JMXServiceURL(s"service:jmx:rmi:///jndi/rmi://$localAddress:$jmxPort/jmxrmi")
    val connectorServer = JMXConnectorServerFactory.newJMXConnectorServer(address, env, server)
    Try(connectorServer.start) match {
      case Success(_) ⇒
        val message = new StringBuilder()
          .append('\n')
          .append("====================================================================================================").append('\n')
          .append(s"★ ★ ★ ★ ★ ★  JMX server available on $localAddress:$jmxPort  ★ ★ ★ ★ ★ ★")
          .append('\n')
          .append("====================================================================================================").append('\n')
          .toString()
        system.log.info(message)
      case Failure(ex) ⇒ system.log.info("JMX server start error {}", ex.getMessage)
    }

    super.startup()
  }
}
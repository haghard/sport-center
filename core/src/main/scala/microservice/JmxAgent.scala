package microservice

import scala.util.Try
import java.rmi.registry.LocateRegistry
import java.lang.management.ManagementFactory
import javax.management.remote.{ JMXConnectorServerFactory, JMXServiceURL }
import microservice.api.{ BootableMicroservice, ClusterNetworkSupport }

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
    Try(connectorServer.start) recover {
      case ex: Throwable ⇒ system.log.info("JMX server start error {}", ex.getMessage)
    }

    super.startup()
  }
}
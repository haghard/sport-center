package microservice.api

import akka.actor.ActorSystem
import java.net.NetworkInterface
import microservice.http.BootableRestService
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}

import scala.collection.JavaConverters._
import BootableClusterNode._

object MicroserviceKernel {
  val ActorSystemName = "SportCenter"
  val microserviceDispatcher = "akka.http-dispatcher"
  val ipExpression = """\d{1,3}.\d{1,3}.\d{1,3}.\d{1,3}"""
}

abstract class MicroserviceKernel(override val akkaSystemPort: String,
                                  override val environment: String,
                                  override val httpPort: Int = BootableClusterNode.DefaultCloudHttpPort,
                                  override val jmxPort: Int = BootableClusterNode.DefaultJmxPort,
                                  override val clusterRole: String = BootableClusterNode.MicroserviceRole,
                                  override val ethName: String = BootableClusterNode.CloudEth)
  extends BootableMicroservice
  with ClusterNetworkSupport 
  with SeedNodesSupport
  with BootableRestService {
  import microservice.api.MicroserviceKernel._

  override lazy val localAddress = NetworkInterface.getNetworkInterfaces.asScala.flatMap { x ⇒
    x.getInetAddresses.asScala.find(x ⇒
      seedAddresses.find(y ⇒ y.getHostAddress == x.getHostAddress).isDefined)
  }.toList.headOption.map(_.getHostAddress).getOrElse {
    NetworkInterface.getNetworkInterfaces.asScala.toList
      .find(x ⇒ x.getName == ethName)
      .flatMap(x ⇒ x.getInetAddresses.asScala.toList.find(_.getHostAddress.matches(ipExpression)))
      .map(_.getHostAddress).getOrElse("0.0.0.0")
  }

  override lazy val system = ActorSystem(ActorSystemName, config)

  private lazy val restApi = configureApi()

  def config = {
    val local = ConfigFactory.empty()
      .withValue("akka.cluster.seed-nodes", ConfigValueFactory.fromIterable(akkaSeedNodes))
      .withFallback(ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$akkaSystemPort"))
      .withFallback(ConfigFactory.parseString(s"akka.remote.netty.tcp.hostname=$localAddress"))
      .withFallback(ConfigFactory.parseString(s"akka.cluster.roles = [${clusterRole}]"))
      .withFallback(ConfigFactory.parseString("akka.contrib.data-replication.gossip-interval = 1 s"))
      .withFallback(ConfigFactory.parseString("akka.cluster.min-nr-of-members = 3"))
      //.withFallback(ConfigFactory.parseString("akka.cluster.role { load-balancer.min-nr-of-members = 3 }"))
      .withFallback(ConfigFactory.load("application.conf"))
      .withFallback(ConfigFactory.load("app-setting.conf"))
      .withFallback(ConfigFactory.load("crawler.conf"))

    if (clusterRole == MicroserviceRole) {
      local.withFallback(ConfigFactory.parseString(s"akka.contrib.cluster.sharding.role=${clusterRole}"))
    }

    local
  }

  override def startup(): Unit = {
    system
    
    val message = new StringBuilder()
      .append('\n')
      .append("=====================================================================================================================================")
      .append('\n')
      .append(s"★ ★ ★ ★ ★ ★   Cluster environment: $environment - Akka-System: $localAddress:$akkaSystemPort  ★ ★ ★ ★ ★ ★")
      .append('\n')
      .append(s"★ ★ ★ ★ ★ ★   Seed nodes: ${config.getStringList("akka.cluster.seed-nodes")} ")
      .append("★ ★ ★ ★ ★ ★")
      .append('\n')
      .append(s"★ ★ ★ ★ ★ ★   Node cluster role: $clusterRole / JMX port: $jmxPort ★ ★ ★ ★ ★ ★")
      .append('\n')
      .append("=====================================================================================================================================")
      .append('\n')
      .toString

    system.log.info(message)

    installApi(restApi)(system, localAddress, httpPort)
  }

  override def shutdown(): Unit = {
    uninstallApi(restApi)
    system.log.info(s"Cluster node $akkaClusterAddress leave a cluster")
  }
}
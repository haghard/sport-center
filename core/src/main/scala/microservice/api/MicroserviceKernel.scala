package microservice.api

import akka.actor.ActorSystem
import java.net.NetworkInterface
import scala.collection.JavaConverters._
import microservice.http.BootableRestService
import com.typesafe.config.ConfigFactory

object MicroserviceKernel {
  val ActorSystemName = "SportCenter"
  val microserviceDispatcher = "akka.http-dispatcher"
  val ipExpression = """\d{1,3}.\d{1,3}.\d{1,3}.\d{1,3}"""

  val CrawlerRole = "Crawler"
  val DomainRole = "Domain" //if you change this name you change in application.conf
  val GatewayRole = "Gateway"
}

abstract class MicroserviceKernel(override val akkaSystemPort: String,
  override val environment: String,
  override val httpPort: Int = BootableClusterNode.DefaultCloudHttpPort,
  override val jmxPort: Int = BootableClusterNode.DefaultJmxPort,
  override val clusterRole: String = MicroserviceKernel.DomainRole,
  override val ethName: String = BootableClusterNode.CloudEth)
    extends BootableMicroservice
    with ClusterNetworkSupport
    with SeedNodesSupport
    with BootableRestService {
  import microservice.api.MicroserviceKernel._

  override lazy val localAddress =
    NetworkInterface.getNetworkInterfaces.asScala.flatMap { x ⇒
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

  private def config = {
    val env = ConfigFactory.load("env.conf")
    val mongoHost = env.getConfig("env.mongo").getString("mh")
    val mongoPort = env.getConfig("env.mongo").getString("mp")

    val seedNodesString = akkaSeedNodes.map { node =>
      s"""akka.cluster.seed-nodes += "akka.tcp://$ActorSystemName@$node:$akkaSystemPort""""
    }.mkString("\n")

    val seeds = (ConfigFactory parseString seedNodesString).resolve()
    println(seeds)

    //ConfigFactory.empty().withValue("akka.cluster.seed-nodes", ConfigValueFactory.fromIterable(akkaSeedNodes))

    val local = ConfigFactory.empty()
      .withFallback(seeds)
      .withFallback(ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$akkaSystemPort"))
      .withFallback(ConfigFactory.parseString(s"akka.remote.netty.tcp.hostname=$localAddress"))
      .withFallback(ConfigFactory.parseString(s"akka.cluster.roles = [${clusterRole}]"))
      .withFallback(ConfigFactory.parseString("akka.contrib.data-replication.gossip-interval = 1 s"))
      .withFallback(ConfigFactory.parseString("akka.cluster.min-nr-of-members = 2"))
      .withFallback(ConfigFactory.parseString(s"""casbah-journal.mongo-journal-url="mongodb://$mongoHost:$mongoPort/sportcenter.journal""""))
      .withFallback(ConfigFactory.parseString(s"""casbah-snapshot-store.mongo-snapshot-url="mongodb://$mongoHost:$mongoPort/sportcenter.snapshot""""))
      .withFallback(ConfigFactory.load("application.conf"))
      .withFallback(ConfigFactory.load("app-setting.conf"))
      .withFallback(ConfigFactory.load("crawler.conf"))

    if (clusterRole == DomainRole)
      local.withFallback(ConfigFactory.parseString(s"akka.contrib.cluster.sharding.role=${clusterRole}"))

    local
  }

  override def startup(): Unit = {
    system

    val message = new StringBuilder().append('\n')
      .append("=====================================================================================================================================")
      .append('\n')
      .append(s"★ ★ ★ ★ ★ ★  Cluster environment: $environment - Akka-System: $localAddress:$akkaSystemPort  ★ ★ ★ ★ ★ ★")
      .append('\n')
      .append(s"★ ★ ★ ★ ★ ★  Mongo-Journal: ${system.settings.config.getString("casbah-journal.mongo-journal-url")}  ★ ★ ★ ★ ★ ★")
      .append('\n')
      .append(s"★ ★ ★ ★ ★ ★  Seed nodes: ${system.settings.config.getStringList("akka.cluster.seed-nodes")}  ★ ★ ★ ★ ★ ★")
      .append('\n')
      .append(s"★ ★ ★ ★ ★ ★  Node cluster role: $clusterRole / JMX port: $jmxPort  ★ ★ ★ ★ ★ ★").append('\n')
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
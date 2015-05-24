package microservice.api

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import microservice.http.BootableRestService

object MicroserviceKernel {
  val ActorSystemName = "SportCenter"
  val microserviceDispatcher = "akka.http-dispatcher"
  val ipExpression = """\d{1,3}.\d{1,3}.\d{1,3}.\d{1,3}"""

  val CrawlerRole = "Crawler"
  val DomainRole = "Domain" //if you change this value you should've changed it in application.conf
  val GatewayRole = "Gateway"
  val AnalyticRole = "Analytic"
}

abstract class MicroserviceKernel(override val akkaSystemPort: String,
  override val environment: String,
  override val httpPort: Int = BootableClusterNode.DefaultCloudHttpPort,
  override val jmxPort: Int = BootableClusterNode.DefaultJmxPort,
  override val clusterRole: String = MicroserviceKernel.DomainRole,
  override val ethName: String = BootableClusterNode.CloudEth) extends BootableMicroservice
    with ClusterNetworkSupport
    with SeedNodesSupport
    with BootableRestService {

  import BootableClusterNode._
  import microservice.api.MicroserviceKernel._

  private lazy val restApi = configureApi()

  override lazy val system = ActorSystem(ActorSystemName, config)

  override def localAddress = seedAddresses.map(_.getHostAddress).getOrElse("0.0.0.0")

  lazy val config = {
    val la = localAddress

    val env = ConfigFactory.load("internals.conf")
    val cassandraEPs = env.getConfig("db.cassandra").getString("seeds")
    val cassandraPort = env.getConfig("db.cassandra").getString("port")
    val contactPoints = cassandraEPs.split(",").map(_.trim).mkString("\"", "\",\"", "\"")

    val akkaSeeds = if (clusterRole == GatewayRole) {
      Option(System.getProperty(SEEDS_ENV_VAR)).map(line => line.split(",").toList)
        .fold(List(s"$la:$akkaSystemPort"))(s"$la:$akkaSystemPort" :: _)
    } else {
      Option(System.getProperty(SEEDS_ENV_VAR))
        .fold(throw new Exception(s"$SEEDS_ENV_VAR env valuable should be defined"))(x => x.split(",").toList)
    }

    val seedNodesString = akkaSeeds.map { node =>
      val ap = node.split(":")
      s"""akka.cluster.seed-nodes += "akka.tcp://$ActorSystemName@${ap(0)}:${ap(1)}""""
    }.mkString("\n")

    val seeds = (ConfigFactory parseString seedNodesString).resolve()

    val local = ConfigFactory.empty().withFallback(seeds)
      .withFallback(ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$akkaSystemPort"))
      .withFallback(ConfigFactory.parseString(s"akka.remote.netty.tcp.hostname=$la"))
      .withFallback(ConfigFactory.parseString(s"akka.cluster.roles = [${clusterRole}]"))
      .withFallback(ConfigFactory.parseString("akka.contrib.data-replication.gossip-interval = 1 s"))
      .withFallback(ConfigFactory.parseString(s"""
        akka.cluster.role {
          $GatewayRole.min-nr-of-members = 2
          $CrawlerRole.min-nr-of-members = 1
        }
       """))
      .withFallback(ConfigFactory.parseString(s"cassandra-journal.contact-points=[$contactPoints]"))
      .withFallback(ConfigFactory.parseString(s"cassandra-snapshot-store.contact-points=[$contactPoints]"))
      .withFallback(ConfigFactory.parseString(s"cassandra-journal.port=$cassandraPort"))
      .withFallback(ConfigFactory.parseString(s"cassandra-snapshot-store.port=$cassandraPort"))
      .withFallback(ConfigFactory.load("application.conf"))
      .withFallback(ConfigFactory.load("app-setting.conf"))
      .withFallback(ConfigFactory.load("crawler.conf"))

    if (clusterRole == DomainRole)
      local.withFallback(ConfigFactory.parseString(s"akka.cluster.sharding.role=${clusterRole}"))

    local
  }

  override def startup(): Unit = {
    system

    val message = new StringBuilder().append('\n')
      .append("=====================================================================================================================================")
      .append('\n')
      .append(s"★ ★ ★ ★ ★ ★  Cluster environment: $environment - Akka-System: $localAddress:$akkaSystemPort  ★ ★ ★ ★ ★ ★")
      .append('\n')
      .append(s"★ ★ ★ ★ ★ ★  Cassandra contact points: ${system.settings.config.getStringList("cassandra-journal.contact-points")}  ★ ★ ★ ★ ★ ★")
      .append('\n')
      .append(s"★ ★ ★ ★ ★ ★  Akka cluster seed nodes: ${system.settings.config.getStringList("akka.cluster.seed-nodes")}  ★ ★ ★ ★ ★ ★")
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
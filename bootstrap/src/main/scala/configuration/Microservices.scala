package configuration

import configuration.local.LocalSeedsResolver
import domain.DomainSupport
import microservice.JmxAgent
import hystrix.HystrixTurbineSupport
import crawler.CrawlerGuardianSupport
import crawler.http.CrawlerMicroservice
import microservice.api.BootableClusterNode._
import java.util.concurrent.ThreadLocalRandom
import http.{ ApiGatewayMicroservice, StandingMicroservice, ResultsMicroservice }
import microservice.api._
import discovery.{ ServiceRegistryCleanerSupport, DiscoveryClientSupport, DiscoveryHttpClient }
import scala.reflect.ClassTag

trait Microservices {

  type NodeIdentity = MicroserviceCfg

  sealed trait MicroserviceCfg {
    def akkaPort: String
    def httpPort: Int
    def jmxPort: Int
    def envName: String
  }

  case class GatewayCfg(val akkaPort: String, val httpPort: Int, val jmxPort: Int, val envName: String) extends MicroserviceCfg
  case class CrawlerCfg(val akkaPort: String, val httpPort: Int, val jmxPort: Int, val envName: String) extends MicroserviceCfg

  case class ResultsQuerySideCfg(val akkaPort: String, val httpPort: Int, val jmxPort: Int, val envName: String) extends MicroserviceCfg
  case class StandingCfg(val akkaPort: String, val httpPort: Int, val jmxPort: Int, val envName: String) extends MicroserviceCfg

  abstract class MicroserviceFactory[T <: NodeIdentity: ClassTag] {
    def create(desc: T): BootableMicroservice
  }

  object local {

    private[Microservices] trait LocalClusterNode[T <: NodeIdentity] {
      def create(desc: T): BootableMicroservice
    }

    implicit def localNode[T <: NodeIdentity: LocalClusterNode: ClassTag]: MicroserviceFactory[T] = {
      new MicroserviceFactory[T] {
        override def create(desc: T) = implicitly[LocalClusterNode[T]].create(desc)
      }
    }
  }

  object container {

    private[Microservices] trait ContainerClusterNode[T <: NodeIdentity] {
      def create(desc: T): BootableMicroservice
    }

    implicit def containerNode[T <: NodeIdentity: ContainerClusterNode: ClassTag]: MicroserviceFactory[T] = {
      new MicroserviceFactory[T] {
        override def create(desc: T) = implicitly[ContainerClusterNode[T]].create(desc)
      }
    }
  }

  object cloud {

    private[Microservices] trait CloudClusterNode[T <: NodeIdentity] {
      def create(desc: T): BootableMicroservice
    }

    implicit def cloudNode[T <: NodeIdentity: CloudClusterNode: ClassTag]: MicroserviceFactory[T] = {
      new MicroserviceFactory[T] {
        override def create(desc: T) = implicitly[CloudClusterNode[T]].create(desc)
      }
    }
  }
}

object Microservices extends Microservices {
  import configuration.Microservices.container._
  import configuration.Microservices.local._

  def randomHttpPort = ThreadLocalRandom.current().nextInt(9000, 9050)
  def randomJmxPort = ThreadLocalRandom.current().nextInt(5000, 6000)

  /*******************************************************************************************************************/
  implicit object ContainerGateway extends ContainerClusterNode[GatewayCfg] {
    override def create(desc: GatewayCfg) = {
      object Gateaway extends MicroserviceKernel(desc.akkaPort, desc.envName, desc.httpPort,
        desc.jmxPort, MicroserviceKernel.GatewayRole, CloudEth)
        with SeedNodesResolver
        with ApiGatewayMicroservice
        with HystrixTurbineSupport
        with ServiceRegistryCleanerSupport
        with JmxAgent
      Gateaway
    }
  }

  implicit object ContainerResultsQuerySide extends ContainerClusterNode[ResultsQuerySideCfg] {
    override def create(cfg: ResultsQuerySideCfg) = {
      object ResultsQuerySide extends MicroserviceKernel(cfg.akkaPort, cfg.envName, cfg.httpPort, cfg.jmxPort, ethName = CloudEth)
        with SeedNodesResolver
        with ResultsMicroservice
        with DiscoveryClientSupport with DiscoveryHttpClient
        with DomainSupport
        with JmxAgent
      ResultsQuerySide
    }
  }

  implicit object ContainerStandingQuerySide extends ContainerClusterNode[StandingCfg] {
    override def create(cfg: StandingCfg) = {
      object StandingQuerySide extends MicroserviceKernel(cfg.akkaPort, cfg.envName, cfg.httpPort, cfg.jmxPort, ethName = CloudEth)
        with SeedNodesResolver
        with StandingMicroservice
        with DiscoveryClientSupport with DiscoveryHttpClient
        with DomainSupport
        with JmxAgent
      StandingQuerySide
    }
  }

  implicit object ContainerCrawlerWriteSide extends ContainerClusterNode[CrawlerCfg] {
    override def create(cfg: CrawlerCfg) = {
      object CrawlerWriteSide extends MicroserviceKernel(cfg.akkaPort, cfg.envName, cfg.httpPort, cfg.jmxPort, MicroserviceKernel.CrawlerRole, CloudEth)
        with SeedNodesResolver
        with CrawlerMicroservice
        with DiscoveryClientSupport with DiscoveryHttpClient
        with CrawlerGuardianSupport
        with JmxAgent
      //with DigitaloceanClient
      CrawlerWriteSide
    }
  }

  /******************************************************************************************************************/
  implicit object LocalGateway extends LocalClusterNode[GatewayCfg] {
    override def create(desc: GatewayCfg) = {
      object Gateway extends MicroserviceKernel(desc.akkaPort, desc.envName, desc.httpPort,
        desc.jmxPort, MicroserviceKernel.GatewayRole, LocalMacEth)
        with LocalSeedsResolver
        with ApiGatewayMicroservice
        with ServiceRegistryCleanerSupport
      Gateway
    }
  }

  implicit object LocalResultsQuerySide extends LocalClusterNode[ResultsQuerySideCfg] {
    override def create(cfg: ResultsQuerySideCfg) = {
      object ResultsQuery extends MicroserviceKernel(cfg.akkaPort, cfg.envName, cfg.httpPort, cfg.jmxPort, ethName = LocalMacEth)
        with LocalSeedsResolver
        with ResultsMicroservice
        with DiscoveryClientSupport with DiscoveryHttpClient
        with DomainSupport
      ResultsQuery
    }
  }

  implicit object LocalCrawler extends LocalClusterNode[CrawlerCfg] {
    override def create(cfg: CrawlerCfg) = {
      object Crawler extends MicroserviceKernel(cfg.akkaPort, cfg.envName, cfg.httpPort, cfg.jmxPort, MicroserviceKernel.CrawlerRole, LocalMacEth)
        with LocalSeedsResolver
        with CrawlerMicroservice
        with DiscoveryClientSupport with DiscoveryHttpClient
        with CrawlerGuardianSupport
      Crawler
    }
  }

  /**
   *
   * This signature means that method ``microservice`` is parameterized by a T,
   * which is required to be a subtype of NodeIdentity.
   * Also an instances of MicroserviceFactory[T] and ClassTag[T] must be implicitly available.
   *
   */
  def microservice[T <: NodeIdentity: MicroserviceFactory: ClassTag](implicit desc: T) =
    implicitly[MicroserviceFactory[T]].create(desc)
}
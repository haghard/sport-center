package configuration

import microservice.api._
import domain.DomainSupport
import microservice.JmxAgent
import hystrix.HystrixTurbineSupport
import crawler.CrawlerGuardianSupport
import crawler.http.CrawlerMicroservice
import microservice.api.BootableClusterNode._
import java.util.concurrent.ThreadLocalRandom
import configuration.local.LocalSeedsResolver
import http.{ ApiGatewayMicroservice, StandingMicroservice, ResultsMicroservice }
import discovery.{ ServiceRegistryCleanerSupport, DiscoveryClientSupport, DiscoveryHttpClient }
import scala.reflect.ClassTag

trait Microservices {

  type NodeIdentity = MicroserviceCfg

  sealed trait MicroserviceCfg {
    def akkaPort: String
    def httpPort: Int
    def jmxPort: Int
    def env: String
  }

  case class GatewayCfg(val akkaPort: String, val httpPort: Int, val jmxPort: Int, val env: String) extends MicroserviceCfg
  case class CrawlerCfg(val akkaPort: String, val httpPort: Int, val jmxPort: Int, val env: String) extends MicroserviceCfg

  case class ResultsQuerySideCfg(val akkaPort: String, val httpPort: Int, val jmxPort: Int, val env: String) extends MicroserviceCfg
  case class StandingCfg(val akkaPort: String, val httpPort: Int, val jmxPort: Int, val env: String) extends MicroserviceCfg

  case class AnalyticsCfg(val akkaPort: String, val httpPort: Int, val jmxPort: Int, val env: String) extends MicroserviceCfg

  abstract class MicroserviceFactory[T <: NodeIdentity: ClassTag] {
    def apply(desc: T): BootableMicroservice
  }

  object local {

    private[Microservices] trait LocalClusterNode[T <: NodeIdentity] {
      def create(desc: T): BootableMicroservice
    }

    implicit def localNode[T <: NodeIdentity: LocalClusterNode: ClassTag]: MicroserviceFactory[T] = {
      new MicroserviceFactory[T] {
        override def apply(desc: T) = implicitly[LocalClusterNode[T]].create(desc)
      }
    }
  }

  object container {

    private[Microservices] trait ContainerClusterNode[T <: NodeIdentity] {
      def create(desc: T): BootableMicroservice
    }

    implicit def containerNode[T <: NodeIdentity: ContainerClusterNode: ClassTag]: MicroserviceFactory[T] = {
      new MicroserviceFactory[T] {
        override def apply(desc: T) = implicitly[ContainerClusterNode[T]].create(desc)
      }
    }
  }

  object cloud {

    private[Microservices] trait CloudClusterNode[T <: NodeIdentity] {
      def create(desc: T): BootableMicroservice
    }

    implicit def cloudNode[T <: NodeIdentity: CloudClusterNode: ClassTag]: MicroserviceFactory[T] = {
      new MicroserviceFactory[T] {
        override def apply(desc: T) = implicitly[CloudClusterNode[T]].create(desc)
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
      object Gateway extends MicroserviceKernel(desc.akkaPort, desc.env, desc.httpPort,
        desc.jmxPort, MicroserviceKernel.GatewayRole, CloudEth)
        with SeedNodesResolver
        with ApiGatewayMicroservice
        with HystrixTurbineSupport
        with ServiceRegistryCleanerSupport
        with JmxAgent
      Gateway
    }
  }

  implicit object ContainerResultsQuerySide extends ContainerClusterNode[ResultsQuerySideCfg] {
    override def create(cfg: ResultsQuerySideCfg) = {
      object ResultsQuerySide extends MicroserviceKernel(cfg.akkaPort, cfg.env, cfg.httpPort, cfg.jmxPort, ethName = CloudEth)
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
      object StandingQuerySide extends MicroserviceKernel(cfg.akkaPort, cfg.env, cfg.httpPort, cfg.jmxPort, ethName = CloudEth)
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
      object CrawlerWriteSide extends MicroserviceKernel(cfg.akkaPort, cfg.env, cfg.httpPort, cfg.jmxPort, MicroserviceKernel.CrawlerRole, CloudEth)
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
      object Gateway extends MicroserviceKernel(desc.akkaPort, desc.env, desc.httpPort,
        desc.jmxPort, MicroserviceKernel.GatewayRole, LocalMacEth)
        with LocalSeedsResolver
        with ApiGatewayMicroservice
        with ServiceRegistryCleanerSupport
      Gateway
    }
  }

  implicit object LocalResultsQuerySide extends LocalClusterNode[ResultsQuerySideCfg] {
    override def create(cfg: ResultsQuerySideCfg) = {
      object ResultsQuery extends MicroserviceKernel(cfg.akkaPort, cfg.env, cfg.httpPort, cfg.jmxPort, ethName = LocalMacEth)
        with LocalSeedsResolver
        with ResultsMicroservice
        with DiscoveryClientSupport with DiscoveryHttpClient
        with DomainSupport
      ResultsQuery
    }
  }

  implicit object LocalStandingQuerySide extends LocalClusterNode[StandingCfg] {
    override def create(cfg: StandingCfg) = {
      object StandingQuery extends MicroserviceKernel(cfg.akkaPort, cfg.env, cfg.httpPort, cfg.jmxPort, ethName = LocalMacEth)
        with LocalSeedsResolver
        with StandingMicroservice
        with DiscoveryClientSupport with DiscoveryHttpClient
        with DomainSupport
      StandingQuery
    }
  }

  implicit object LocalCrawler extends LocalClusterNode[CrawlerCfg] {
    override def create(cfg: CrawlerCfg) = {
      object Crawler extends MicroserviceKernel(cfg.akkaPort, cfg.env, cfg.httpPort, cfg.jmxPort, MicroserviceKernel.CrawlerRole, LocalMacEth)
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
    implicitly[MicroserviceFactory[T]].apply(desc)
}
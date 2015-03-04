package configuration

import java.util.concurrent.ThreadLocalRandom

import crawler.ChangeSetWriterSupport
import crawler.http.CrawlerMicroservice
import discovery.{ DiscoveryClientSupport, DiscoveryHttpClient }
import domain.DomainSupport
import http.{ StandingMicroservice, ResultsMicroservice }
import microservice.JmxAgent
import microservice.api.BootableClusterNode._
import microservice.api.{ BootableMicroservice, LocalSeedNodesClient, MicroserviceKernel }
import services.gateway.ApiGatewayMicroservice
import services.hystrix.HystrixTurbineSupport

import scala.reflect.ClassTag

trait Microservices {

  sealed trait MicroserviceCfg {
    def akkaPort: String
    def httpPort: Int
    def jmxPort: Int
    def envName: String
  }

  type NodeIdentity = MicroserviceCfg

  case class LoadBalancerCfg(val akkaPort: String, val httpPort: Int, val jmxPort: Int, val envName: String) extends MicroserviceCfg
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
  import configuration.Microservices.local._

  def randomHttpPort = ThreadLocalRandom.current().nextInt(9000, 9050)
  def randomJmxPort = ThreadLocalRandom.current().nextInt(5000, 6000)

  implicit object LocalLoadBalancer extends LocalClusterNode[LoadBalancerCfg] {
    override def create(desc: LoadBalancerCfg) = {
      object LocalLoadBalancerNode extends MicroserviceKernel(desc.akkaPort, desc.envName, desc.httpPort, desc.jmxPort, GatewayRole, LocalEth)
        with LocalSeedNodesClient
        with ApiGatewayMicroservice
        with HystrixTurbineSupport
        with JmxAgent
      LocalLoadBalancerNode
    }
  }

  implicit object LocalResultsQuerySide extends LocalClusterNode[ResultsQuerySideCfg] {
    override def create(cfg: ResultsQuerySideCfg) = {
      object LocalBackend extends MicroserviceKernel(cfg.akkaPort, cfg.envName, cfg.httpPort, cfg.jmxPort, ethName = LocalEth)
        with LocalSeedNodesClient
        with ResultsMicroservice
        with DiscoveryClientSupport with DiscoveryHttpClient
        with DomainSupport
      //with JmxAgent
      LocalBackend
    }
  }

  implicit object LocalStandingQuerySide extends LocalClusterNode[StandingCfg] {
    override def create(cfg: StandingCfg) = {
      object LocalStanding extends MicroserviceKernel(cfg.akkaPort, cfg.envName, cfg.httpPort, cfg.jmxPort, ethName = LocalEth)
        with LocalSeedNodesClient
        with StandingMicroservice
        with DiscoveryClientSupport with DiscoveryHttpClient
        with DomainSupport
      //with JmxAgent
      LocalStanding
    }
  }

  implicit object LocalCrawlerWriteSide extends LocalClusterNode[CrawlerCfg] {
    override def create(cfg: CrawlerCfg) = {
      object LocalCrawler extends MicroserviceKernel(cfg.akkaPort, cfg.envName, cfg.httpPort, cfg.jmxPort, CrawlerRole, LocalEth)
        with LocalSeedNodesClient
        with CrawlerMicroservice
        with DiscoveryClientSupport with DiscoveryHttpClient
        with ChangeSetWriterSupport
      //with JmxAgent
      //with DigitaloceanClient
      LocalCrawler
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
package discovery

import akka.actor._
import akka.cluster.Cluster
import akka.contrib.datareplication.Replicator.{ Subscribe, UpdateResponse }
import akka.contrib.datareplication.{ DataReplication, LWWMap, Replicator }
import akka.pattern.{ AskTimeoutException, ask }
import spray.json.DefaultJsonProtocol

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scalaz.{ -\/, \/, \/- }

object ServiceDiscovery extends ExtensionKey[ServiceDiscovery] {

  val DataKey = "service-discovery"

  case class KV(address: String, url: String)

  sealed trait KeyOps { def key: KV }

  case class SetKey(override val key: KV) extends KeyOps
  case class UnsetKey(override val key: KV) extends KeyOps
  case class UnsetAddress(val key: String)

  case class Update(r: UpdateResponse)

  case class DiscoveryLine(address: String, urls: Set[String])
  object DiscoveryLine extends DefaultJsonProtocol { implicit val format = jsonFormat2(apply) }

  case class Registry(items: mutable.HashMap[String, Set[String]])

  case class UnknownKey(name: String) extends IllegalStateException(s"Key [$name] doesn't exist!")

  override def get(system: ActorSystem): ServiceDiscovery = super.get(system)

  override def lookup(): ExtensionId[ServiceDiscovery] = ServiceDiscovery

  override def createExtension(system: ExtendedActorSystem) = new ServiceDiscovery(system)
}

class ServiceDiscovery(system: ExtendedActorSystem) extends Extension {
  import discovery.ServiceDiscovery._

  private val config = system.settings.config.getConfig("discovery")
  private val timeout = config.getDuration("ops-timeout", SECONDS).second

  private val readTimeout = timeout
  private val writeTimeout = timeout

  private implicit val cluster = Cluster(system)

  private implicit val sys = system

  private val replicator = DataReplication(system).replicator

  private def --(map: LWWMap[DiscoveryLine], kv: KV): LWWMap[DiscoveryLine] = {
    map.get(kv.address) match {
      case Some(DiscoveryLine(_, urls)) ⇒
        val restUrls = urls - kv.url
        if (restUrls.isEmpty) map - kv.address
        else map + (kv.address -> DiscoveryLine(kv.address, restUrls))
      case None ⇒
        system.log.info(s"Service ${kv.address} already has been deleted")
        throw UnknownKey(kv.address)
    }
  }

  private def --(map: LWWMap[DiscoveryLine], key: String): LWWMap[DiscoveryLine] = {
    map.get(key) match {
      case Some(DiscoveryLine(_, urls)) ⇒ map - key
      case None ⇒
        system.log.info("Service already has been deleted")
        throw UnknownKey(key)
    }
  }

  private def ++(map: LWWMap[DiscoveryLine], k: KV): LWWMap[DiscoveryLine] =
    map.get(k.address) match {
      case Some(DiscoveryLine(_, existingUrls)) ⇒ map + (k.address -> DiscoveryLine(k.address, existingUrls + k.url))
      case None                                 ⇒ map + (k.address -> DiscoveryLine(k.address, Set(k.url)))
    }

  def subscribe(subscriber: ActorRef): Unit = {
    replicator ! Subscribe(ServiceDiscovery.DataKey, subscriber)
  }

  def setKey(op: SetKey)(implicit ec: ExecutionContext): Future[String \/ Update] =
    replicator
      .ask(update(map ⇒ ++(map, op.key)))(writeTimeout)
      .mapTo[UpdateResponse]
      .map {
        case r@Replicator.UpdateSuccess(DataKey, _) ⇒ \/-(Update(r))
        case response ⇒ -\/(s"SetKey op unexpected response $response")
      }.recoverWith {
        case ex: ClassCastException ⇒ Future.successful(-\/(s"SetKey op error ${ex.getMessage}"))
        case ex: AskTimeoutException ⇒ Future.successful(-\/(s"SetKey op error timeout ${ex.getMessage}"))
      }

  def unsetKey(op: UnsetKey)(implicit ec: ExecutionContext): Future[String \/ Update] = {
    replicator
      .ask(update(map ⇒ --(map, op.key)))(writeTimeout)
      .mapTo[UpdateResponse]
      .flatMap {
        case r @ Replicator.UpdateSuccess(DataKey, _) ⇒ Future.successful(\/-(Update(r)))
        case r @ Replicator.ModifyFailure(DataKey, _, error: UnknownKey, _) ⇒ Future.successful(-\/(s"Delete error $error"))
        case other ⇒ Future.successful(-\/(s"Delete error $other"))
      }
  }

  def deleteAll(op: UnsetAddress)(implicit ec: ExecutionContext): Future[String \/ Update] = {
    replicator
      .ask(update(map ⇒ --(map, op.key)))(writeTimeout)
      .mapTo[UpdateResponse]
      .flatMap {
        case r @ Replicator.UpdateSuccess(DataKey, _) ⇒ Future.successful(\/-(Update(r)))
        case r @ Replicator.ModifyFailure(DataKey, _, error: UnknownKey, _) ⇒ Future.successful(-\/(s"Delete error $error"))
        case other ⇒ Future.successful(-\/(s"Delete error $other"))
      }
  }

  def findAll(implicit ec: ExecutionContext): Future[String \/ Registry] = {
    replicator
      .ask(read)(readTimeout)
      .mapTo[Replicator.GetResponse]
      .flatMap(respond)
      .recoverWith {
        case ex: AskTimeoutException ⇒
          replicator.ask(readLocal)(readTimeout)
            .mapTo[Replicator.GetResponse]
            .flatMap(respond)
        case ex: Exception ⇒
          replicator.ask(readLocal)(readTimeout)
            .mapTo[Replicator.GetResponse]
            .flatMap(respond)
      }
  }

  private def respond: PartialFunction[Replicator.GetResponse, Future[String \/ Registry]] = {
    case Replicator.GetSuccess(DataKey, registry: LWWMap[DiscoveryLine], _) ⇒
      Future.successful {
        \/-(Registry((registry.entries.values map { case line: DiscoveryLine ⇒ line })
          .foldLeft(new mutable.HashMap[String, Set[String]]()) { (acc, c) ⇒
            acc.get(c.address).fold {
              acc += (c.address -> c.urls)
            } { existed ⇒ acc += (c.address -> c.urls.++(existed)) }
          })
        )
      }
    case Replicator.NotFound(DataKey, _) ⇒ Future.successful(-\/(s"NotFound registry"))
    case other                           ⇒ Future.successful(-\/(s"Find error $other"))
  }

  private def read: Replicator.Get =
    Replicator.Get(DataKey, Replicator.ReadQuorum(readTimeout))

  private def readLocal: Replicator.Get =
    Replicator.Get(DataKey, Replicator.ReadLocal)

  private def update(modify: LWWMap[DiscoveryLine] ⇒ LWWMap[DiscoveryLine]): Replicator.Update[LWWMap[DiscoveryLine]] = {
    Replicator.Update(
      DataKey,
      LWWMap.empty[DiscoveryLine],
      Replicator.ReadQuorum(readTimeout),
      Replicator.WriteQuorum(writeTimeout)
    )(modify)
  }
}
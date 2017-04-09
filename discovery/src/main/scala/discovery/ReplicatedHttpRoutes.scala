package discovery

import akka.actor._
import akka.cluster.Cluster
import akka.pattern.{ AskTimeoutException, ask }
import spray.json.DefaultJsonProtocol

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scalaz.{ -\/, \/, \/- }
import akka.cluster.ddata.{ LWWMapKey, Replicator, DistributedData, LWWMap }
import akka.cluster.ddata.Replicator._

object ReplicatedHttpRoutes extends ExtensionKey[ReplicatedHttpRoutes] {
  val DataKey = "service-discovery"

  case class KV(address: String, url: String)

  sealed trait KeyOps { def key: KV }

  case class SetKey(override val key: KV) extends KeyOps
  case class UnsetKey(override val key: KV) extends KeyOps
  case class UnsetAddress(val key: String)

  case class Update(r: UpdateResponse[LWWMap[HttpRouteLine]])

  case class HttpRouteLine(address: String, urls: Set[String])
  object HttpRouteLine extends DefaultJsonProtocol { implicit val format = jsonFormat2(apply) }

  case class Registry(items: mutable.HashMap[String, Set[String]])

  case class UnknownKey(name: String) extends IllegalStateException(s"Key [$name] doesn't exist!")

  override def get(system: ActorSystem): ReplicatedHttpRoutes = super.get(system)

  override def lookup(): ExtensionId[ReplicatedHttpRoutes] = ReplicatedHttpRoutes

  override def createExtension(system: ExtendedActorSystem) = new ReplicatedHttpRoutes(system)
}

class ReplicatedHttpRoutes(system: ExtendedActorSystem) extends Extension {
  import discovery.ReplicatedHttpRoutes._

  private val config = system.settings.config.getConfig("discovery")
  private val timeout = config.getDuration("ops-timeout", SECONDS).second

  private val readTimeout = timeout
  private val writeTimeout = timeout

  private implicit val cluster = Cluster(system)

  private implicit val sys = system

  private val replicator = DistributedData(system).replicator

  private def --(map: LWWMap[HttpRouteLine], kv: KV): LWWMap[HttpRouteLine] = {
    map.get(kv.address) match {
      case Some(HttpRouteLine(_, urls)) ⇒
        val restUrls = urls - kv.url
        if (restUrls.isEmpty) map - kv.address
        else map + (kv.address -> HttpRouteLine(kv.address, restUrls))
      case None ⇒
        system.log.info(s"Service ${kv.address} already has been deleted")
        throw UnknownKey(kv.address)
    }
  }

  private def --(map: LWWMap[HttpRouteLine], key: String): LWWMap[HttpRouteLine] = {
    map.get(key) match {
      case Some(HttpRouteLine(_, urls)) ⇒ map - key
      case None ⇒
        system.log.info("Service already has been deleted")
        throw UnknownKey(key)
    }
  }

  private def ++(map: LWWMap[HttpRouteLine], k: KV): LWWMap[HttpRouteLine] =
    map.get(k.address) match {
      case Some(HttpRouteLine(_, existingUrls)) ⇒ map + (k.address -> HttpRouteLine(k.address, existingUrls + k.url))
      case None ⇒ map + (k.address -> HttpRouteLine(k.address, Set(k.url)))
    }

  def subscribe(subscriber: ActorRef): Unit = {
    replicator ! Subscribe(LWWMapKey[HttpRouteLine](DataKey), subscriber)
  }

  def setKey(op: SetKey)(implicit ec: ExecutionContext): Future[String \/ Update] =
    replicator.ask(update(map ⇒ ++(map, op.key)))(writeTimeout).mapTo[UpdateResponse[LWWMap[HttpRouteLine]]]
      .map {
        case r @ Replicator.UpdateSuccess(LWWMapKey(DataKey), _) ⇒ \/-(Update(r))
        case response ⇒ -\/(s"SetKey op unexpected response $response")
      } recoverWith {
        case ex: ClassCastException ⇒
          Future.successful(-\/(s"SetKey op ClassCastException ${ex.getMessage}"))
        case ex: AskTimeoutException ⇒
          Future.successful(-\/(s"SetKey op AskTimeoutException ${ex.getMessage}"))
      }

  def unsetKey(op: UnsetKey)(implicit ec: ExecutionContext): Future[String \/ Update] = {
    replicator
      .ask(update(map ⇒ --(map, op.key)))(writeTimeout)
      .mapTo[UpdateResponse[LWWMap[HttpRouteLine]]]
      .flatMap {
        case r @ Replicator.UpdateSuccess(LWWMapKey(DataKey), _) ⇒ Future.successful(\/-(Update(r)))
        case r @ Replicator.ModifyFailure(LWWMapKey(DataKey), _, error: UnknownKey, _) ⇒ Future.successful(-\/(s"Delete error $error"))
        case other ⇒ Future.successful(-\/(s"Delete error $other"))
      }
  }

  def deleteAll(op: UnsetAddress)(implicit ec: ExecutionContext): Future[String \/ Update] = {
    replicator
      .ask(update(map ⇒ --(map, op.key)))(writeTimeout)
      .mapTo[UpdateResponse[LWWMap[HttpRouteLine]]]
      .flatMap {
        case r @ Replicator.UpdateSuccess(LWWMapKey(DataKey), _) ⇒ Future.successful(\/-(Update(r)))
        case r @ Replicator.ModifyFailure(LWWMapKey(DataKey), _, error: UnknownKey, _) ⇒ Future.successful(-\/(s"Delete error $error"))
        case other ⇒ Future.successful(-\/(s"Delete error $other"))
      }
  }

  def findAll(implicit ec: ExecutionContext): Future[String \/ Registry] = {
    replicator
      .ask(read)(readTimeout)
      .mapTo[Replicator.GetResponse[LWWMap[HttpRouteLine]]]
      .flatMap(respond)
      .recoverWith {
        case ex: AskTimeoutException ⇒
          replicator.ask(readLocal)(readTimeout)
            .mapTo[Replicator.GetResponse[LWWMap[HttpRouteLine]]]
            .flatMap(respond)
        case ex: Exception ⇒
          replicator.ask(readLocal)(readTimeout)
            .mapTo[Replicator.GetResponse[LWWMap[HttpRouteLine]]]
            .flatMap(respond)
      }
  }

  private def respond: PartialFunction[Replicator.GetResponse[LWWMap[HttpRouteLine]], Future[String \/ Registry]] = {
    case res @ Replicator.GetSuccess(LWWMapKey(DataKey), _) =>
      Future.successful {
        \/-(Registry(res.dataValue.entries.values.map { case line: HttpRouteLine ⇒ line }
          .foldLeft(new mutable.HashMap[String, Set[String]]()) { (acc, c) ⇒
            acc.get(c.address).fold(acc += (c.address -> c.urls)) { existed ⇒ acc += (c.address -> c.urls.++(existed)) }
          }))
      }
    case Replicator.NotFound(LWWMapKey(DataKey), _) ⇒ Future.successful(-\/(s"NotFound registry"))
    case other ⇒ Future.successful(-\/(s"Find error $other"))
  }

  private def read: Replicator.Get[LWWMap[HttpRouteLine]] =
    Replicator.Get(LWWMapKey[HttpRouteLine](DataKey), Replicator.ReadMajority(readTimeout))

  private def readLocal: Replicator.Get[LWWMap[HttpRouteLine]] =
    Replicator.Get(LWWMapKey[HttpRouteLine](DataKey), Replicator.ReadLocal)

  private def update(modify: LWWMap[HttpRouteLine] ⇒ LWWMap[HttpRouteLine]): Replicator.Update[LWWMap[HttpRouteLine]] =
    Replicator.Update(
      LWWMapKey[HttpRouteLine](DataKey),
      LWWMap.empty[HttpRouteLine],
      Replicator.WriteMajority(writeTimeout)
    )(modify)
}
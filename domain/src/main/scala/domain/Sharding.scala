package domain

import akka.cluster.sharding.ShardRegion.CurrentRegions
import akka.util.Timeout
import domain.TeamAggregate.TeamMessage
import akka.pattern.{ AskTimeoutException, ask }
import akka.actor.{ ActorRef, ActorSystem, Props }
import microservice.domain.{ Command, QueryCommand, State }
import akka.cluster.sharding.{ ClusterShardingSettings, ShardRegion, ClusterSharding }

import scala.concurrent.{ ExecutionContext, Future }
import scala.reflect.ClassTag

trait Sharding {

  protected def system: ActorSystem

  protected def props: Props

  protected def shardCount: Int

  protected def name: String = TeamAggregate.shardName

  def start() = ClusterSharding(system).start(
    typeName = name,
    entityProps = props,
    settings = ClusterShardingSettings(system).withRememberEntities(true),
    extractEntityId = idExtractor,
    extractShardId = shardResolver(shardCount)
  )

  protected def tellEntry[T <: QueryCommand](command: T)(implicit sender: ActorRef): Unit =
    shardRegion ! command

  protected def writeEntry[T <: Command](command: T)(implicit sender: ActorRef): Unit =
    shardRegion ! command

  protected def askEntry[T <: State](command: QueryCommand)(implicit timeout: Timeout, sender: ActorRef, ec: ExecutionContext, tag: ClassTag[T]): Future[T] =
    shardRegion
      .ask(command)
      .mapTo[T]
      .recoverWith {
        case ex: AskTimeoutException ⇒ Future.failed[T](new Exception(ex))
        case ex: ClassCastException ⇒ Future.failed[T](new Exception(ex))
      }

  protected def showLocalRegions(implicit timeout: Timeout): Future[CurrentRegions] =
    (shardRegion ? ShardRegion.GetCurrentRegions).mapTo[CurrentRegions]

  private def idExtractor: ShardRegion.ExtractEntityId = {
    case m: TeamMessage ⇒ (m.aggregateRootId, m)
  }

  //may be to use division name for ShardId
  private def shardResolver(shardCount: Int): ShardRegion.ExtractShardId = {
    case (m: TeamMessage) ⇒ (m.aggregateRootId.hashCode % shardCount).toString
  }

  protected lazy val shardRegion =
    ClusterSharding(system).shardRegion(name)
}

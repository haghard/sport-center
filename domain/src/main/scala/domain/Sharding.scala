package domain

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.contrib.pattern.{ ClusterSharding, ShardRegion }
import akka.pattern.{ AskTimeoutException, ask }
import akka.util.Timeout
import domain.TeamAggregate.TeamMessage
import microservice.domain.{ Command, QueryCommand, State }

import scala.concurrent.{ ExecutionContext, Future }
import scala.reflect.ClassTag

trait Sharding {

  protected def system: ActorSystem

  protected def props: Props

  protected def shardCount: Int

  protected def typeName = TeamAggregate.shardName

  def start() = ClusterSharding(system).start(typeName, Some(props), idExtractor, shardResolver(shardCount))

  protected def tellEntry[T <: QueryCommand](command: T)(implicit sender: ActorRef): Unit = {
    shardRegion ! command
  }

  protected def writeEntry[T <: Command](command: T)(implicit sender: ActorRef): Unit = {
    shardRegion ! command
  }

  protected def askEntry[T <: State](command: QueryCommand)(implicit timeout: Timeout, sender: ActorRef, ec: ExecutionContext, tag: ClassTag[T]): Future[T] =
    shardRegion
      .ask(command)
      .mapTo[T]
      .recoverWith {
        case ex: AskTimeoutException ⇒ Future.failed[T](new Exception(ex))
        case ex: ClassCastException  ⇒ Future.failed[T](new Exception(ex))
      }

  private def idExtractor: ShardRegion.IdExtractor = {
    case m: TeamMessage ⇒ (m.aggregateRootId, m)
  }

  private def shardResolver(shardCount: Int): ShardRegion.ShardResolver = {
    case (m: TeamMessage) ⇒ (m.aggregateRootId.hashCode % shardCount).toString
  }

  def shardRegion = ClusterSharding(system).shardRegion(typeName)
}

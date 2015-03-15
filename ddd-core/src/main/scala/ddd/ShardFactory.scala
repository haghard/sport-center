package ddd

import akka.actor.ActorRef
import scala.reflect.ClassTag

abstract class ShardFactory[T <: BusinessEntity: BusinessEntityActorFactory: ShardResolution: ClassTag] {

  def getOrCreate: ActorRef

  def shardName = implicitly[ClassTag[T]].runtimeClass.getSimpleName
}
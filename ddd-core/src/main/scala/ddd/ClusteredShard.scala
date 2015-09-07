package ddd

import akka.cluster.sharding.{ ClusterShardingSettings, ClusterSharding }
import akka.cluster.sharding.ShardRegion.Passivate

import scala.reflect.ClassTag
import akka.actor.{ PoisonPill, ActorSystem, ActorRef }

object ClusteredShard {

  implicit def clusteredShardFactory[T <: BusinessEntity: BusinessEntityActorFactory: ShardResolution: ClassTag](implicit system: ActorSystem) = {
    new ShardFactory[T] {
      private def region: Option[ActorRef] = {
        try {
          Some(ClusterSharding(system).shardRegion(shardName))
        } catch {
          case ex: IllegalArgumentException => None
        }
      }

      override def getOrCreate: ActorRef = {
        region.getOrElse {
          startSharding()
          region.get
        }
      }

      private def startSharding(): Unit = {
        val entityFactory = implicitly[BusinessEntityActorFactory[T]]
        val EntityProps = entityFactory.props(new PassivationConfig(Passivate(PoisonPill), entityFactory.inactivityTimeout))
        val entityClass = implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]]
        val sr = implicitly[ShardResolution[T]]

        ClusterSharding(system)
          .start(entityClass.getSimpleName, EntityProps,
            ClusterShardingSettings(system).withRememberEntities(true),
            sr.idExtractor, sr.shardResolver)
      }
    }
  }
}
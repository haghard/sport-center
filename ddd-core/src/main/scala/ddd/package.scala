import java.util.{ Date, UUID }

import akka.actor._
import org.joda.time.DateTime
import ddd.BusinessEntity.EntityId
import microservice.domain.DomainEvent
import ddd.IdResolution.EntityIdResolver
import scala.concurrent.duration.Duration
import ddd.ShardResolution.ShardResolutionStrategy
import akka.contrib.pattern.ShardRegion.{ IdExtractor, ShardResolver }

import scala.util.{ Success, Try }

package object ddd {

  object MetaData {
    val DeliveryId = "_deliveryId"
    val CorrelationId: String = "correlationId"
  }

  trait Receipt

  object alod {
    /**
     * At-Least-Once Delivery
     *
     */
    trait Delivered extends Receipt {
      def deliveryId: Long
    }
    case class Received(deliveryId: Long) extends Delivered
    case class Acknowledge(deliveryId: Long, result: Try[Any] = Success("OK")) extends Delivered
  }

  object amod {

    /**
     * At-Most-Once Delivery
     *
     */
    case class Acknowledge(result: Try[Any] = Success("OK")) extends Receipt
    case class EffectlessAck(result: Try[Any] = Success("OK")) extends Receipt
  }

  class MetaData(var metadata: Map[String, Any] = Map.empty) extends Serializable {

    def withMetaData(metadata: Option[MetaData]): MetaData = {
      if (metadata.isDefined) withMetaData(metadata.get.metadata) else this
    }

    def withMetaData(metadata: Map[String, Any], clearExisting: Boolean = false): MetaData = {
      if (clearExisting) {
        this.metadata = Map.empty
      }
      new MetaData(this.metadata ++ metadata)
    }

    def contains(attrName: String) = metadata.contains(attrName)

    def get[B](attrName: String) = tryGet[B](attrName).get

    def tryGet[B](attrName: String): Option[B] = metadata.get(attrName).asInstanceOf[Option[B]]

    def exceptDeliveryAttributes: Option[MetaData] = {
      val resultMap = this.metadata.filterKeys(a => a.startsWith("_"))
      if (resultMap.isEmpty) None else Some(new MetaData(resultMap))
    }

    override def toString: String = metadata.toString()
  }

  abstract class Message(var metadata: Option[MetaData] = None) extends Serializable {

    def id: String

    type MessageImpl <: Message

    def metadataExceptDeliveryAttributes: Option[MetaData] = {
      metadata.flatMap(_.exceptDeliveryAttributes)
    }

    def withMetaData(metadata: Option[MetaData]): MessageImpl = {
      if (metadata.isDefined) withMetaData(metadata.get.metadata) else this.asInstanceOf[MessageImpl]
    }

    def withMetaData(metadata: Map[String, Any], clearExisting: Boolean = false): MessageImpl = {
      this.metadata = Some(this.metadata.getOrElse(new MetaData()).withMetaData(metadata))
      this.asInstanceOf[MessageImpl]
    }

    def withMetaAttribute(attrName: Any, value: Any): MessageImpl = withMetaData(Map(attrName.toString -> value))

    def hasMetaAttribute(attrName: Any) = if (metadata.isDefined) metadata.get.contains(attrName.toString) else false

    def getMetaAttribute[B](attrName: Any) = tryGetMetaAttribute[B](attrName).get

    def tryGetMetaAttribute[B](attrName: Any): Option[B] = if (metadata.isDefined) metadata.get.tryGet[B](attrName.toString) else None

    def deliveryAck(result: Try[Any] = Success("OK")): Receipt = {
      if (deliveryId.isDefined) alod.Acknowledge(deliveryId.get, result)
      else amod.Acknowledge(result)
    }

    def withDeliveryId(deliveryId: Long) = withMetaAttribute(MetaData.DeliveryId, deliveryId)

    def deliveryId: Option[Long] = tryGetMetaAttribute[Any](MetaData.DeliveryId).map {
      case bigInt: scala.math.BigInt => bigInt.toLong
      case l: Long                   => l
    }
  }

  object EventMessage {
    def unapply(em: EventMessage): Option[(String, DomainEvent)] = Some(em.id, em.event)
  }

  class EventMessage(val event: DomainEvent,
      val id: String = UUID.randomUUID().toString,
      val timestamp: DateTime = new DateTime) extends Message with EntityMessage {

    type MessageImpl = EventMessage

    override def entityId = tryGetMetaAttribute[String](MetaData.CorrelationId).orNull
    override def payload = event

    override def toString: String = {
      val msgClass = getClass.getSimpleName
      s"$msgClass(event = $event, identifier = $id, timestamp = $timestamp, metaData = $metadata)"
    }
  }

  case class AggregateSnapshotId(aggregateId: EntityId, sequenceNr: Long = 0)

  case class DomainEventMessage(snapshotId: AggregateSnapshotId,
    override val event: DomainEvent,
    override val id: String = UUID.randomUUID().toString,
    override val timestamp: DateTime = new DateTime)
      extends EventMessage(event, id, timestamp) {

    override def entityId = aggregateId

    def this(em: EventMessage, s: AggregateSnapshotId) = this(s, em.event, em.id, em.timestamp)

    def aggregateId = snapshotId.aggregateId

    def sequenceNr = snapshotId.sequenceNr
  }

  trait EntityMessage {
    def entityId: EntityId
    def payload: Any
  }

  trait DomainCommand {
    def aggregateId: String
  }

  case class CommandMessage(command: DomainCommand, id: String = UUID.randomUUID().toString,
      timestamp: Date = new Date) extends Message with EntityMessage {
    override def entityId: EntityId = command.aggregateId
    override def payload: Any = command
  }

  object BusinessEntity {
    type EntityId = String
  }

  trait BusinessEntity {
    def id: EntityId
  }

  object IdResolution {
    type EntityIdResolver = PartialFunction[Any, EntityId]
  }

  trait IdResolution[A] {
    def entityIdResolver: EntityIdResolver
  }

  abstract class BusinessEntityActorFactory[A <: BusinessEntity] {
    def props(pc: PassivationConfig): Props
    def inactivityTimeout: Duration
  }

  object ShardResolution {

    type ShardResolutionStrategy = EntityIdResolver => ShardResolver

    private val defaultShardResolutionStrategy: ShardResolutionStrategy = {
      entityIdResolver =>
        {
          case msg => Integer.toHexString(entityIdResolver(msg).hashCode).charAt(0).toString
        }
    }
  }

  trait ShardResolution[A] extends IdResolution[A] {

    def shardResolutionStrategy: ShardResolutionStrategy = ShardResolution.defaultShardResolutionStrategy

    val shardResolver: ShardResolver = shardResolutionStrategy(entityIdResolver)

    val idExtractor: IdExtractor = {
      case em: EntityMessage => (entityIdResolver(em), em)
      case c: DomainCommand  => (entityIdResolver(c), CommandMessage(c))
    }
  }

  class CustomShardResolution[T] extends ShardResolution[T] {
    override def entityIdResolver: EntityIdResolver = {
      case em: EntityMessage => em.entityId
    }
  }

}
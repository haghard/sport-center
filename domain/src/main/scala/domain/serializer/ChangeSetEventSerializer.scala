package domain.serializer

import akka.actor.ExtendedActorSystem
import akka.serialization.Serializer
import domain.formats.DomainEventFormats.ChangeSetFormat
import domain.update.DomainWriter.BeginTransaction

final class ChangeSetEventSerializer(system: ExtendedActorSystem) extends Serializer {
  private val EventClass = classOf[BeginTransaction]

  override val identifier = 18

  override val includeManifest = true

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = manifest match {
    case None => ChangeSetFormat.parseFrom(bytes)
    case Some(c) => c match {
      case EventClass => toDomainEvent(ChangeSetFormat.parseFrom(bytes))
      case _          => throw new IllegalArgumentException(s"can't deserialize object of type ${c}")
    }
  }

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case e: BeginTransaction => eventBuilder(e).build().toByteArray
    case _                   => throw new IllegalArgumentException(s"can't serialize object of type ${o.getClass}")
  }

  private def eventBuilder(e: BeginTransaction) =
    ChangeSetFormat.newBuilder().setSeqNumber(e.sn).setSize(e.size)

  private def toDomainEvent(format: ChangeSetFormat): BeginTransaction =
    BeginTransaction(format.getSeqNumber, format.getSize)
}
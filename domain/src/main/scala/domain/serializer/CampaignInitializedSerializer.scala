package domain.serializer

import akka.actor.{ ExtendedActorSystem, ActorSystem }
import akka.serialization.Serializer
import domain.formats.DomainEventFormats.CampaignInitializedFormat
import microservice.crawler._
import org.joda.time.DateTime

final class CampaignInitializedSerializer(system: ActorSystem) extends Serializer {
  private val EventClass = classOf[domain.CrawlerCampaign.CampaignInitialized]

  def this(system: ExtendedActorSystem) = {
    this(system.asInstanceOf[ActorSystem])
  }

  override val identifier = 31

  override val includeManifest = true

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = manifest match {
    case None => CampaignInitializedFormat parseFrom bytes
    case Some(c) => c match {
      case EventClass =>
        val bts = (CampaignInitializedFormat parseFrom bytes)
        //system.log.info("CampaignInitialized fromBinary persisted event serialized-size: {}", bts.getSerializedSize)
        toDomainEvent(bts)
      case other => throw new IllegalArgumentException(s"Can't serialize object of type ${other}")
    }
  }

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case e: domain.CrawlerCampaign.CampaignInitialized =>
      val bts = eventBuilder(e).build().toByteArray
      //system.log.info("CampaignPersistedEvent toBinary. Length: {}", bts.length)
      bts
    case _ => throw new IllegalArgumentException(s"Can't serialize object of type ${o.getClass}")
  }

  private def eventBuilder(e: domain.CrawlerCampaign.CampaignInitialized) = {
    CampaignInitializedFormat.newBuilder()
      .setAggregateRootId(e.aggregateRootId)
      .setDate(e.dt.getTime)
  }

  private def toDomainEvent(f: CampaignInitializedFormat) =
    domain.CrawlerCampaign.CampaignInitialized(
      f.getAggregateRootId,
      new DateTime(f.getDate).withZone(SCENTER_TIME_ZONE).toDate
    )
}

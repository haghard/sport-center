package domain.serializer

import akka.actor.ExtendedActorSystem
import akka.serialization.Serializer
import domain.CrawlerCampaign.CampaignPersistedEvent
import domain.formats.DomainEventFormats.PersistedCampaignFormat
import microservice.crawler._
import org.joda.time.DateTime
import scala.collection.JavaConverters._

/**
 *
 * `CampaignBeingPersisted` is being read by `NbaChangeDataCaptureSubscriber` and `NbaCampaignView`
 * and written by `CrawlerCampaign`
 *
 * @param system
 *
 */
final class CampaignPersistedSerializer(system: ExtendedActorSystem) extends Serializer {
  private val EventClass = classOf[CampaignPersistedEvent]

  override val identifier = 20

  override val includeManifest = true

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = manifest match {
    case None => PersistedCampaignFormat.parseFrom(bytes)
    case Some(c) => c match {
      case EventClass => toDomainEvent(PersistedCampaignFormat.parseFrom(bytes))
      case _          => throw new IllegalArgumentException(s"can't deserialize object of type ${c}")
    }
  }

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case e: CampaignPersistedEvent => eventBuilder(e).build().toByteArray
    case _                         => throw new IllegalArgumentException(s"can't serialize object of type ${o.getClass}")
  }

  private def toDomainEvent(f: PersistedCampaignFormat): CampaignPersistedEvent = {
    CampaignPersistedEvent(
      f.getAggregateRootId,
      new DateTime(f.getDate).withZone(SCENTER_TIME_ZONE).toDate,
      f.getResultsList.asScala.foldRight(List[NbaResult]()) { (r, acc) =>
        NbaResult(r.getHomeTeam, r.getHomeScore, r.getAwayTeam, r.getAwayScore, new DateTime(r.getTime).withZone(SCENTER_TIME_ZONE).toDate) :: acc
      }
    )
  }

  private def eventBuilder(e: CampaignPersistedEvent) = {
    val b = PersistedCampaignFormat.newBuilder()
    b
  }
}
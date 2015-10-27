package crawler.serializer

import akka.actor.ExtendedActorSystem
import akka.serialization.Serializer
import ddd.EventMessage
import domain.CrawlerCampaign.{ CampaignInitialized, CampaignPersistedEvent }
import domain.formats.EventMessageFormats._
import microservice.crawler._
import org.joda.time.DateTime
import scala.collection.JavaConverters._

class EventMessageSerialized(system: ExtendedActorSystem) extends Serializer {

  private val EventClass = classOf[EventMessage]

  override def identifier = 31

  override def includeManifest = true

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = manifest match {
    case None => EventMessageFormat.parseFrom(bytes)
    case Some(c) => c match {
      case EventClass => toDomainEvent(EventMessageFormat.parseFrom(bytes))
      case _          => throw new IllegalArgumentException(s"can't deserialize object of type ${c}")
    }
  }

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case e: EventMessage => builder(e).build().toByteArray
    case _               => throw new IllegalArgumentException(s"can't serialize object of type ${o.getClass}")
  }

  private def toDomainEvent(format: EventMessageFormat): EventMessage = {
    val e = format.getEvent
    val list = e.getEventsList.asScala.foldRight(List[NbaResult]()) { (r, acc) =>
      NbaResult(r.getHomeTeam, r.getHomeScore, r.getAwayTeam, r.getAwayScore,
        new DateTime(r.getTime).withZone(SCENTER_TIME_ZONE).toDate) :: acc
    }

    new EventMessage(
      CampaignPersistedEvent(e.getOwner, new DateTime(e.getTime).withZone(SCENTER_TIME_ZONE).toDate, list),
      format.getUuid, new DateTime(format.getTime).withZone(SCENTER_TIME_ZONE))
  }

  private def builder(e: EventMessage): EventMessageFormat.Builder = {
    val b = EventMessageFormat.newBuilder()
    b.setTime(e.timestamp.toDate.getTime).setUuid(e.id)
    var pb = domain.formats.EventMessageFormats.PersistedEventFormat.newBuilder()
    e.event match {
      case e: CampaignInitialized =>
        pb.setOwner(e.aggregateRootId).setTime(e.dt.getTime)
      case e: CampaignPersistedEvent =>
        pb.setOwner(e.aggregateRootId).setTime(e.dt.getTime)
        e.results.foreach { r =>
          val b0 = domain.formats.EventMessageFormats.ResultFormat.newBuilder()
          b0.setHomeTeam(r.homeTeam).setHomeScore(r.homeScore).setAwayTeam(r.awayTeam).setAwayScore(r.awayScore)
            .setTime(r.dt.getTime)
          pb = pb.addEvents(b0)
        }
    }

    b.setEvent(pb)
  }
}
package crawler.serializer

import java.util.Date
import akka.actor.ExtendedActorSystem
import akka.serialization.Serializer
import crawler.writer.CrawlerGuardian.CrawlerResponse
import domain.formats.CrawlerResponseFormats.CrawlerResponseFormat
import microservice.crawler._
import org.joda.time.DateTime
import scala.collection.JavaConverters._

final class CrawlerResponseSerializer(system: ExtendedActorSystem) extends Serializer {
  private val EventClass = classOf[CrawlerResponse]

  override val identifier = 17

  override val includeManifest = true

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = manifest match {
    case None => CrawlerResponseFormat.parseFrom(bytes)
    case Some(c) => c match {
      case EventClass => toDomainEvent(CrawlerResponseFormat.parseFrom(bytes))
      case _ => throw new IllegalArgumentException(s"can't deserialize object of type ${c}")
    }
  }

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case e: CrawlerResponse => builder(e).build().toByteArray
    case _ => throw new IllegalArgumentException(s"can't serialize object of type ${o.getClass}")
  }

  private def toDomainEvent(format: CrawlerResponseFormat): CrawlerResponse =
    CrawlerResponse(
      new DateTime(format.getLogicalTime).withZone(SCENTER_TIME_ZONE),
      format.getResultsList.asScala.foldRight(List[NbaResult]()) { (r, acc) =>
        NbaResult(r.getHomeTeam, r.getHomeScore, r.getAwayTeam, r.getAwayScore, new Date(r.getTime)) :: acc
      }
    )

  private def builder(e: CrawlerResponse) = {
    var builder = CrawlerResponseFormat.newBuilder()
    builder.setLogicalTime(e.dt.toDate.getTime)

    e.results.foreach { r =>
      val b = domain.formats.CrawlerResponseFormats.ResponseFormat.newBuilder()
      b.setHomeTeam(r.homeTeam).setHomeScore(r.homeScore).setAwayTeam(r.awayTeam).setAwayScore(r.awayScore)
      b.setTime(r.dt.getTime)
      builder = builder.addResults(b)
    }

    builder
  }
}
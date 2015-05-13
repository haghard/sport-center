package domain.serializer

import java.util.Date

import akka.actor.ExtendedActorSystem
import akka.serialization.Serializer
import domain.TeamAggregate.ResultAdded
import domain.formats.DomainEventFormats.{ ResultFormat, ResultAddedEvent }
import microservice.crawler.NbaResult

final class ResultAddedEventSerializer(system: ExtendedActorSystem) extends Serializer {
  val EventClass = classOf[ResultAdded]

  override val identifier = 19

  override val includeManifest = true

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]) = manifest match {
    case None => ResultAddedEvent.parseFrom(bytes)
    case Some(c) => c match {
      case EventClass => toDomainEvent(ResultAddedEvent.parseFrom(bytes))
      case _          => throw new IllegalArgumentException(s"can't deserialize object of type ${c}")
    }
  }

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case e: ResultAdded => eventBuilder(e).build().toByteArray
    case _              => throw new IllegalArgumentException(s"can't serialize object of type ${o.getClass}")
  }

  private def toDomainEvent(e: ResultAddedEvent): ResultAdded = {
    val r = e.getResult
    ResultAdded(e.getTeamName, NbaResult(r.getHomeTeam, r.getHomeScore, r.getAwayTeam, r.getAwayScore, new Date(r.getTime)))
  }

  private def eventBuilder(e: ResultAdded) = {
    val res = e.r
    val resLine = ResultFormat.newBuilder()
      .setHomeTeam(res.homeTeam).setHomeScore(res.homeScore)
      .setAwayTeam(res.awayTeam).setAwayScore(res.awayScore)
      .setTime(res.dt.getTime).build()

    ResultAddedEvent.newBuilder().setTeamName(e.team).setResult(resLine)
  }
}
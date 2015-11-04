package domain.serializer

import akka.actor.{ ActorSystem, ExtendedActorSystem }
import akka.serialization.Serializer
import domain.TeamAggregate.ResultAdded
import domain.formats.DomainEventFormats.{ ResultFormat, ResultAddedEvent }
import microservice.crawler._
import org.joda.time.DateTime
import scala.collection.JavaConverters._

final class ResultAddedEventSerializer(system: ActorSystem) extends Serializer {
  val EventClass = classOf[ResultAdded]

  def this(system: ExtendedActorSystem) {
    this(system.asInstanceOf[ActorSystem])
  }

  override val identifier = 19

  override val includeManifest = true

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]) = manifest match {
    case None => (ResultAddedEvent parseFrom bytes)
    case Some(c) => c match {
      case EventClass =>
        //system.log.info("ResultAdded fromBinary. Length:{}", bytes.length)
        toDomainEvent(ResultAddedEvent parseFrom bytes)
      case _ => throw new IllegalArgumentException(s"can't deserialize object of type ${c}")
    }
  }

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case e: ResultAdded =>
      val bts = eventBuilder(e).build().toByteArray
      //system.log.info("Persisted batch size: {}", bts.length)
      bts
    case _ => throw new IllegalArgumentException(s"can't serialize object of type ${o.getClass}")
  }

  private def toDomainEvent(e: ResultAddedEvent): ResultAdded = {
    val r = e.getResult
    val ht = r.getHomeTotal
    val at = r.getAwayTotal

    ResultAdded(e.getTeamName,
      NbaResult(r.getHomeTeam, r.getHomeScore, r.getAwayTeam, r.getAwayScore,
        new DateTime(r.getTime).withZone(SCENTER_TIME_ZONE).toDate,
        r.getHomeScoreLine,
        r.getAwayScoreLine,
        Total(ht.getMinutes, ht.getFgmA, ht.getThreePmA, ht.getFtmA, ht.getMinusSlashPlus, ht.getOffReb,
          ht.getDefReb, ht.getTotalReb, ht.getAst, ht.getPf, ht.getSteels, ht.getTo, ht.getBs, ht.getBa, ht.getPts),
        Total(at.getMinutes, at.getFgmA, at.getThreePmA, at.getFtmA, at.getMinusSlashPlus, at.getOffReb,
          at.getDefReb, at.getTotalReb, at.getAst, at.getPf, at.getSteels, at.getTo, at.getBs, at.getBa, at.getPts),
        r.getHomeScoreBoxList.asScala.foldRight(List.empty[PlayerLine]) { (f, acc) =>
          PlayerLine(f.getName, f.getPos, f.getMin, f.getFgmA, f.getThreePmA, f.getFtmA, f.getMinusSlashPlus, f.getOffReb,
            f.getDefReb, f.getTotalReb, f.getAst, f.getPf, f.getSteels, f.getTo, f.getBs, f.getBa, f.getPts) :: acc
        },
        r.getAwayScoreBoxList.asScala.foldRight(List.empty[PlayerLine]) { (f, acc) =>
          PlayerLine(f.getName, f.getPos, f.getMin, f.getFgmA, f.getThreePmA, f.getFtmA, f.getMinusSlashPlus, f.getOffReb,
            f.getDefReb, f.getTotalReb, f.getAst, f.getPf, f.getSteels, f.getTo, f.getBs, f.getBa, f.getPts) :: acc
        }
      ))
  }

  private def eventBuilder(e: ResultAdded): ResultAddedEvent.Builder = {
    val res = e.r
    val format = ResultFormat.newBuilder()
      .setHomeTeam(res.homeTeam).setHomeScore(res.homeScore)
      .setAwayTeam(res.awayTeam).setAwayScore(res.awayScore)
      .setTime(res.dt.getTime)
      .setHomeScoreLine(res.homeScoreBox)
      .setAwayScoreLine(res.awayScoreBox)

    format.setHomeTotal(domain.formats.DomainEventFormats.TotalFormat.newBuilder()
      .setMinutes(res.homeTotal.min)
      .setFgmA(res.homeTotal.fgmA)
      .setThreePmA(res.homeTotal.threePmA)
      .setFtmA(res.homeTotal.ftmA)
      .setMinusSlashPlus(res.homeTotal.minusSlashPlus)
      .setOffReb(res.homeTotal.offReb)
      .setDefReb(res.homeTotal.defReb)
      .setTotalReb(res.homeTotal.totalReb)
      .setAst(res.homeTotal.ast)
      .setPf(res.homeTotal.pf)
      .setSteels(res.homeTotal.steels)
      .setTo(res.homeTotal.to)
      .setBs(res.homeTotal.bs)
      .setBa(res.homeTotal.ba)
      .setPts(res.homeTotal.pts)
      .build())

    format.setAwayTotal(domain.formats.DomainEventFormats.TotalFormat.newBuilder()
      .setMinutes(res.awayTotal.min)
      .setFgmA(res.awayTotal.fgmA)
      .setThreePmA(res.awayTotal.threePmA)
      .setFtmA(res.awayTotal.ftmA)
      .setMinusSlashPlus(res.awayTotal.minusSlashPlus)
      .setOffReb(res.awayTotal.offReb)
      .setDefReb(res.awayTotal.defReb)
      .setTotalReb(res.awayTotal.totalReb)
      .setAst(res.awayTotal.ast)
      .setPf(res.awayTotal.pf)
      .setSteels(res.awayTotal.steels)
      .setTo(res.awayTotal.to)
      .setBs(res.awayTotal.bs)
      .setBa(res.awayTotal.ba)
      .setPts(res.awayTotal.pts)
      .build())

    res.homeBox.foreach { r =>
      format.addHomeScoreBox(
        domain.formats.DomainEventFormats.PlayerLine.newBuilder()
          .setName(r.name).setPos(r.pos).setMin(r.min)
          .setFgmA(r.fgmA).setThreePmA(r.threePmA).setFtmA(r.ftmA)
          .setMinusSlashPlus(r.minusSlashPlus).setOffReb(r.offReb)
          .setDefReb(r.defReb).setTotalReb(r.totalReb)
          .setAst(r.ast).setPf(r.pf).setSteels(r.steels)
          .setTo(r.to).setBs(r.bs).setBa(r.ba).setPts(r.pts)
          .build())
    }

    res.awayBox.foreach { r =>
      format.addAwayScoreBox(
        domain.formats.DomainEventFormats.PlayerLine.newBuilder()
          .setName(r.name).setPos(r.pos).setMin(r.min)
          .setFgmA(r.fgmA).setThreePmA(r.threePmA).setFtmA(r.ftmA)
          .setMinusSlashPlus(r.minusSlashPlus).setOffReb(r.offReb)
          .setDefReb(r.defReb).setTotalReb(r.totalReb)
          .setAst(r.ast).setPf(r.pf).setSteels(r.steels)
          .setTo(r.to).setBs(r.bs).setBa(r.ba).setPts(r.pts)
          .build())
    }

    ResultAddedEvent.newBuilder()
      .setTeamName(e.team)
      .setResult(format.build())
  }
}
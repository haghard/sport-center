package domain.serializer

import akka.actor.{ ActorSystem, ExtendedActorSystem }
import akka.serialization.Serializer
import domain.CrawlerCampaign.CampaignPersistedEvent
import domain.formats.DomainEventFormats.CampaignPersistedFormat
import microservice.crawler._
import org.joda.time.DateTime
import scala.collection.JavaConverters._

final class CampaignPersistedSerializer(system: ActorSystem) extends Serializer {
  private val EventClass = classOf[CampaignPersistedEvent]

  def this(system: ExtendedActorSystem) = {
    this(system.asInstanceOf[ActorSystem])
  }

  override val identifier = 20

  override val includeManifest = true

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = manifest match {
    case None => (CampaignPersistedFormat parseFrom bytes)
    case Some(c) => c match {
      case EventClass =>
        val bts = (CampaignPersistedFormat parseFrom bytes)
        toDomainEvent(bts)
      case other =>
        throw new IllegalArgumentException(s"Can't deserialize object of type $c to $other")
    }
  }

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case e: CampaignPersistedEvent => eventBuilder(e).build().toByteArray
    case _ => throw new IllegalArgumentException(s"Can't serialize object of type ${o.getClass}")
  }

  private def buildScoreBox(lines: java.util.List[domain.formats.DomainEventFormats.PlayerLine]): List[PlayerLine] = {
    lines.asScala.foldRight(List.empty[PlayerLine]) { (c, acc) =>
      PlayerLine(c.getName, c.getPos, c.getMin,
        c.getFgmA, c.getThreePmA, c.getFtmA, c.getMinusSlashPlus,
        c.getOffReb, c.getDefReb, c.getTotalReb, c.getAst, c.getPf,
        c.getSteels, c.getTo, c.getBs, c.getBa, c.getPts) :: acc
    }
  }

  private def buildTotal(tf: domain.formats.DomainEventFormats.TotalFormat) =
    Total(tf.getMinutes, tf.getFgmA, tf.getThreePmA, tf.getFtmA, tf.getMinusSlashPlus,
      tf.getOffReb, tf.getDefReb, tf.getTotalReb, tf.getAst, tf.getPf, tf.getSteels,
      tf.getTo, tf.getBs, tf.getBa, tf.getPts)

  private def toDomainEvent(f: CampaignPersistedFormat): CampaignPersistedEvent =
    CampaignPersistedEvent(
      f.getAggregateRootId,
      new DateTime(f.getDate).withZone(SCENTER_TIME_ZONE).toDate,
      f.getResultsList.asScala.foldRight(List[NbaResult]()) { (r, acc) =>
        NbaResult(r.getHomeTeam, r.getHomeScore, r.getAwayTeam, r.getAwayScore,
          new DateTime(r.getTime).withZone(SCENTER_TIME_ZONE).toDate,
          r.getHomeScoreLine, r.getAwayScoreLine,
          buildTotal(r.getHomeTotal), buildTotal(r.getAwayTotal),
          buildScoreBox(r.getHomeScoreBoxList), buildScoreBox(r.getAwayScoreBoxList)) :: acc
      }
    )

  private def eventBuilder(e: CampaignPersistedEvent) = {
    val builder = CampaignPersistedFormat.newBuilder()

    e.results.foreach { r =>
      val fr = domain.formats.DomainEventFormats.ResultFormat.newBuilder()
        .setHomeTeam(r.homeTeam)
        .setAwayTeam(r.awayTeam)
        .setHomeScore(r.homeScore)
        .setAwayScore(r.awayScore)
        .setTime(r.dt.getTime)
        .setHomeScoreLine(r.homeScoreBox)
        .setAwayScoreLine(r.awayScoreBox)

        .setHomeTotal(domain.formats.DomainEventFormats.TotalFormat.newBuilder()
          .setMinutes(r.homeTotal.min)
          .setFgmA(r.homeTotal.fgmA)
          .setThreePmA(r.homeTotal.threePmA)
          .setFtmA(r.homeTotal.ftmA)
          .setMinusSlashPlus(r.homeTotal.minusSlashPlus)
          .setOffReb(r.homeTotal.offReb)
          .setDefReb(r.homeTotal.defReb)
          .setTotalReb(r.homeTotal.totalReb)
          .setAst(r.homeTotal.ast)
          .setPf(r.homeTotal.pf)
          .setSteels(r.homeTotal.steels)
          .setTo(r.homeTotal.to)
          .setBs(r.homeTotal.bs)
          .setBa(r.homeTotal.ba)
          .setPts(r.homeTotal.pts)
          .build())

        .setAwayTotal(domain.formats.DomainEventFormats.TotalFormat.newBuilder()
          .setMinutes(r.awayTotal.min)
          .setFgmA(r.awayTotal.fgmA)
          .setThreePmA(r.awayTotal.threePmA)
          .setFtmA(r.awayTotal.ftmA)
          .setMinusSlashPlus(r.awayTotal.minusSlashPlus)
          .setOffReb(r.awayTotal.offReb)
          .setDefReb(r.awayTotal.defReb)
          .setTotalReb(r.awayTotal.totalReb)
          .setAst(r.awayTotal.ast)
          .setPf(r.awayTotal.pf)
          .setSteels(r.awayTotal.steels)
          .setTo(r.awayTotal.to)
          .setBs(r.awayTotal.bs)
          .setBa(r.awayTotal.ba)
          .setPts(r.awayTotal.pts)
          .build())

      r.homeBox.foreach { r =>
        fr.addHomeScoreBox(
          domain.formats.DomainEventFormats.PlayerLine.newBuilder()
            .setName(r.name).setPos(r.pos).setMin(r.min)
            .setFgmA(r.fgmA).setThreePmA(r.threePmA).setFtmA(r.ftmA)
            .setMinusSlashPlus(r.minusSlashPlus).setOffReb(r.offReb)
            .setDefReb(r.defReb).setTotalReb(r.totalReb)
            .setAst(r.ast).setPf(r.pf).setSteels(r.steels)
            .setTo(r.to).setBs(r.bs).setBa(r.ba).setPts(r.pts)
            .build()
        )
      }

      r.awayBox.foreach { r =>
        fr.addAwayScoreBox(
          domain.formats.DomainEventFormats.PlayerLine.newBuilder()
            .setName(r.name).setPos(r.pos).setMin(r.min)
            .setFgmA(r.fgmA).setThreePmA(r.threePmA).setFtmA(r.ftmA)
            .setMinusSlashPlus(r.minusSlashPlus).setOffReb(r.offReb)
            .setDefReb(r.defReb).setTotalReb(r.totalReb)
            .setAst(r.ast).setPf(r.pf).setSteels(r.steels)
            .setTo(r.to).setBs(r.bs).setBa(r.ba).setPts(r.pts)
            .build()
        )
      }

      builder.addResults(fr)
    }

    builder
      .setAggregateRootId(e.aggregateRootId)
      .setDate(e.dt.getTime)
  }
}
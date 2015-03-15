package domain

import java.util.Date

import com.github.nscala_time.time.Imports._
import ddd._
import domain.CrawlerCampaign.CampaignState
import microservice.crawler._
import microservice.domain.DomainEvent
import org.joda.time.DateTime

import scala.annotation.tailrec
import scala.collection.immutable
import scala.util.Success

object CrawlerCampaign {

  case class CrawlerTask(job: Option[CrawlerJob])

  private val timeOffset = 1.day + 2.hour

  private val urlPrefix = "http://www.nba.com/gameline"

  type DateTimeProp = org.joda.time.DateTime.Property

  val alignProp: (DateTimeProp) ⇒ String =
    prop ⇒
      if (prop.get() < 10) s"0${prop.get()}"
      else prop.get().toString

  case class CampaignInitializationError(message: String)

  sealed trait CampaignEvent extends DomainEvent {
    val aggregateRootId: String
  }

  case class CampaignBeingPersisted(aggregateRootId: String, dt: Date, results: immutable.List[NbaResult]) extends CampaignEvent
  case class CampaignBeingInited(aggregateRootId: String, dt: Date) extends CampaignEvent

  case class InitCampaign(date: Date, aggregateId: String = "nba") extends DomainCommand
  case class RequestCampaign(size: Int, aggregateId: String = "nba") extends DomainCommand

  case class PersistCampaign(dt: DateTime, results: immutable.List[NbaResult], aggregateId: String = "nba") extends DomainCommand

  case class CampaignState(name: Option[String] = None,
      progressDate: Option[Date] = None) extends AggregateState {
    override def apply = {
      case CampaignBeingInited(id, dt)      => copy(Option(id), Option(dt))
      case CampaignBeingPersisted(_, dt, _) => copy(progressDate = Option(dt))
      case r: RequestCampaign               => this
    }
  }
}

class CrawlerCampaign(override val pc: PassivationConfig) extends AggregateRoot[CampaignState] with IdempotentReceiver {
  import CrawlerCampaign._

  private val formatter = estFormatter()

  override def factory: ARStateFactory = {
    case CampaignBeingInited(id, dt) =>
      CampaignState(Option(id), Option(dt))
  }

  override def handleCommand: Receive = {
    case InitCampaign(dt, id) =>
      if (initialized) {
        replyTo ! ddd.amod.EffectlessAck(Success("OK"))
      } else raise(CampaignBeingInited(id, dt))

    case RequestCampaign(size, _) =>
      if (initialized) {
        onNext(size)
      } else {
        replyTo ! CampaignInitializationError("Campaign needs to be initialized first")
      }

    case PersistCampaign(dt, results, id) =>
      if (initialized) {
        if (dt.toDate.after(state.progressDate.get)) {
          raise(CampaignBeingPersisted(id, dt.toDate, results))
        } else {
          log.info("[Current campaign {}] - [Received dt {}]]. Do effectless ack", state, dt)
          raiseDuplicate(CampaignBeingPersisted(id, dt.toDate, results))
        }
      } else {
        replyTo ! CampaignInitializationError("Campaign needs to be initialized first")
      }
  }

  private def onNext(batchSize: Int) = {
    val localBatchSize = batchSize
    val lastCrawlDate = new DateTime(state.progressDate.get).withZone(SCENTER_TIME_ZONE)
    val crawlLimitDate = new DateTime().withZone(SCENTER_TIME_ZONE) - timeOffset

    log.info("Crawl before: {}. Last crawler date: {}", formatter.format(crawlLimitDate.toDate), formatter.format(lastCrawlDate.toDate))

    @tailrec
    def loop(acc: List[String], fromDate: DateTime, toDate: DateTime, batchSize: Int): (DateTime, List[String]) = {
      if (fromDate.isBefore(toDate) && batchSize > 0) {
        val nextDate = fromDate + Period.days(1)
        val path = s"""${nextDate.year().get()}${alignProp(nextDate.monthOfYear())}${alignProp(nextDate.dayOfMonth())}/"""
        log.info(s"URL $urlPrefix/$path has been formed")
        loop(acc :+ s"$urlPrefix/$path", nextDate, toDate, batchSize - 1)
      } else (fromDate, acc)
    }

    loop(List.empty[String], lastCrawlDate, crawlLimitDate, localBatchSize) match {
      case (_, Nil)                     ⇒ replyTo ! CrawlerTask(None)
      case (dt, elements: List[String]) ⇒ replyTo ! CrawlerTask(Some(CrawlerJob(dt, elements)))
    }
  }
}
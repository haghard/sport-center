/*
package domain

import akka.actor._
import akka.persistence._
import com.github.nscala_time.time.Imports._
import domain.CrawlCampaignAggregate.CampaignState
import domain.TeamAggregate.MakeSnapshot
import microservice.crawler.{ CrawlerJob, NbaResult, estFormatter }
import microservice.domain.{ Command, DomainEvent, State }
import org.joda.time.DateTime
import microservice.crawler._

import scala.annotation.tailrec
import scala.collection.immutable

object CrawlCampaignAggregate {

  private val timeOffset = 1.day + 2.hour

  private val urlPrefix = "http://www.nba.com/gameline"

  private val startDate = new DateTime().withZone(SCENTER_TIME_ZONE)
    .withDate(2012, 10, 29).withTime(23, 59, 59, 0)

  type DateTimeProp = org.joda.time.DateTime.Property

  val alignProp: (DateTimeProp) ⇒ String =
    prop ⇒
      if (prop.get() < 10) s"0${prop.get()}"
      else prop.get().toString

  case class CrawlerDate(date: DateTime) extends State
  case class CampaignDateChanged(deliveryId: Long, dt: DateTime) extends DomainEvent
  case class CreateCampaign(currentDt: DateTime)

  case object ProgressCampaign

  case class BatchSavedConfirm(dt: DateTime) extends Command with DomainEvent

  case class SaveCompletedBatch(dt: DateTime, results: immutable.List[NbaResult])

  case class CollectedChangeSet(dt: DateTime, results: immutable.List[NbaResult]) extends DomainEvent

  case class CampaignState(date: DateTime = startDate, results: immutable.List[NbaResult] = List()) extends State

  val AggregateId = "crawl-campaign"

  def props(batchSize: Int) = Props(new CrawlCampaignAggregate(batchSize))
}

class CrawlCampaignAggregate private (batchSize: Int, var state: CampaignState = CampaignState()) extends PersistentActor
    with ActorLogging {
  import CrawlCampaignAggregate._

  override def persistenceId = AggregateId

  private val formatter = estFormatter()

  override def receiveCommand: Receive = {
    case cmd @ SaveCompletedBatch(dt, results) ⇒
      persist(CollectedChangeSet(dt, results)) { e ⇒
        log.info("ChangeSet {} has been persisted", formatter.format(dt.toDate))
        //TODO: remove it. This is for debug only
        //TimeUnit.SECONDS.sleep(4)
        updateState(e)
        sender() ! BatchSavedConfirm(dt)
      }

    case MakeSnapshot                         ⇒ saveSnapshot(state)
    case SaveSnapshotSuccess(metadata)        ⇒ log.info("Success restore from snapshot {}", metadata)
    case SaveSnapshotFailure(metadata, cause) ⇒ log.info("Failure restore from snapshot {}", cause.getMessage)

    case ProgressCampaign                     ⇒ progress(sender())
    case PersistenceFailure(payload, seqNum, cause) ⇒
      log.info("Journal fails to write a event: {}", cause.getMessage)
  }

  override def receiveRecover: Receive = {
    case dt: CollectedChangeSet ⇒ updateState(dt)
    case RecoveryCompleted      ⇒ log.info("NbaCampaign recovered with: {}", formatter.format(state.date.toDate))
    case RecoveryFailure(cause) ⇒ log.info(s"NbaCampaignAggregate recovery failure $cause.getMessage")
  }

  private def updateState(event: DomainEvent) {
    event match {
      case CollectedChangeSet(dt, results) ⇒
        state = state.copy(dt, results)
    }
  }

  private def progress(replyTo: ActorRef) = {
    val localBatchSize = batchSize
    val lastCrawlDate = state.date
    val crawlLimitDate = new DateTime().withZone(SCENTER_TIME_ZONE) - timeOffset

    log.info("Crawl before: {}. Last crawler date: {}", formatter format crawlLimitDate.toDate, formatter format lastCrawlDate.toDate)

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
}*/

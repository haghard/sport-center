package crawler.writer

import akka.actor.{ Actor, ActorLogging, Props, ReceiveTimeout }
import akka.pattern.ask
import crawler.CrawlerMaster
import domain.CrawlCampaignAggregate
import CrawlCampaignAggregate.{ BatchSavedConfirm, CrawlerTask, NextCampaign, SaveCompletedBatch }
import microservice.crawler._
import microservice.settings.CustomSettings

import scala.concurrent.duration._
import scala.util.{ Failure, Success }

object ChangeSetBatchWriter {
  def props(settings: CustomSettings) = Props(new ChangeSetBatchWriter(settings))
}

class ChangeSetBatchWriter private (settings: CustomSettings) extends Actor with ActorLogging {
  private val crawlDaysAtOnce = 7
  private val formatter = searchFormatter()

  private val iterationInterval = 1 hours
  private val jobTimeout = 60 seconds

  private val crawlerMaster = context.actorOf(CrawlerMaster.props(settings), "crawler-master")
  private val campaignAggregate = context.actorOf(CrawlCampaignAggregate.props(crawlDaysAtOnce), "crawl-campaign")

  implicit val timeout = akka.util.Timeout(10 seconds)
  implicit val ec = context.system.dispatchers.lookup("scheduler-dispatcher")

  override def preStart = campaignAggregate ! NextCampaign

  private def scheduleCampaign = {
    campaignAggregate ! NextCampaign
    context become receive
  }

  override def receive: Receive = {
    case CrawlerTask(None) ⇒
      log.info("Prevent useless crawler iteration. Data is up to date")
      context.system.scheduler.scheduleOnce(iterationInterval)(campaignAggregate ! NextCampaign)

    case CrawlerTask(Some(job)) ⇒
      log.info("Schedule crawler job up to {} date", formatter format job.endDt.toDate)
      context setReceiveTimeout jobTimeout
      crawlerMaster ! job
      context become waitForCrawler
  }

  private val waitForCrawler: Receive = {
    case _: JobFailed | _: ReceiveTimeout ⇒
      log.info("Crawler job failed cause timeout/failed")
      scheduleCampaign

    case CollectedResultBlock(dt, results) ⇒
      log.info("We have received response from job [{}] - size: {}", dt, results.size)
      campaignAggregate
        .ask(SaveCompletedBatch(dt, results))
        .mapTo[BatchSavedConfirm]
        .onComplete {
          case Success(c) ⇒
            log.info("ChangeSet up to {} was confirmed ", formatter format c.dt.toDate)
            scheduleCampaign
          case Failure(error) ⇒
            log.info("ChangeSet save error {}", error.getMessage)
            context become receive
            context.system.scheduler.scheduleOnce(iterationInterval)(campaignAggregate ! NextCampaign)
        }
  }
}
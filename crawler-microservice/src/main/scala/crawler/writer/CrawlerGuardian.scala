package crawler.writer

import akka.pattern.ask
import ddd.amod.{ EffectlessAck, Acknowledge }
import domain.CrawlerCampaign
import domain.CrawlerCampaign.{ InitCampaign, CrawlerTask, PersistCampaign, RequestCampaign }
import crawler.CrawlerMaster
import ddd.{ PassivationConfig, AggregateRootActorFactory, CustomShardResolution }
import microservice.crawler._
import microservice.settings.CustomSettings
import akka.actor._
import org.joda.time.DateTime

import scala.concurrent.duration._
import scala.util.{ Failure, Success }

object CrawlerGuardian {

  private val startDate = new DateTime().withZone(SCENTER_TIME_ZONE)
    .withDate(2012, 10, 29).withTime(23, 59, 59, 0)

  def props(settings: CustomSettings) = Props(new CrawlerGuardian(settings))
}

class CrawlerGuardian private (settings: CustomSettings) extends Actor with ActorLogging {
  import ddd.Shard._
  import ddd.LocalShard._
  import CrawlerGuardian._

  private val crawlDaysAtOnce = 7
  private val formatter = searchFormatter()

  private val iterationInterval = 1 hours
  private val jobTimeout = 60 seconds

  private val crawlerMaster = context.actorOf(CrawlerMaster.props(settings), "crawler-master")
  //private val campaignAggregate = context.actorOf(CrawlCampaignAggregate.props(crawlDaysAtOnce), "crawl-campaign")

  implicit object ShardResolution extends CustomShardResolution[CrawlerCampaign]
  implicit object agFactory extends AggregateRootActorFactory[CrawlerCampaign] {
    override def inactivityTimeout: Duration = 10.minute
    override def props(pc: PassivationConfig) = Props(new CrawlerCampaign(pc))
  }

  implicit val sys = context.system

  private val campaignDomain: ActorRef = shardOf[CrawlerCampaign]

  implicit val timeout = akka.util.Timeout(10 seconds)
  implicit val ec = context.system.dispatchers.lookup("scheduler-dispatcher")

  override def preStart = campaignDomain ! InitCampaign(startDate.toDate)

  private def scheduleCampaign = {
    campaignDomain ! RequestCampaign(crawlDaysAtOnce)
    context become receive
  }

  override def receive: Receive = {
    case Acknowledge(Success("OK")) | EffectlessAck(Success("OK")) =>
      campaignDomain ! RequestCampaign(crawlDaysAtOnce)

    case CrawlerTask(None) ⇒
      context.system.scheduler.scheduleOnce(iterationInterval)(campaignDomain ! RequestCampaign(crawlDaysAtOnce))
      log.info("Prevent useless crawler iteration. Data is up to date")

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
      campaignDomain
        .ask(PersistCampaign(dt, results))
        .mapTo[ddd.amod.Acknowledge]
        //campaignAggregate
        //.ask(SaveCompletedBatch(dt, results))
        //.mapTo[BatchSavedConfirm]
        .onComplete {
          case Success(c) ⇒
            log.info("ChangeSet was persisted")
            scheduleCampaign
          case Failure(error) ⇒
            log.info("ChangeSet save error {}", error.getMessage)
            context become receive
            //context.system.scheduler.scheduleOnce(iterationInterval)(campaignAggregate ! ProgressCampaign)
            context.system.scheduler.scheduleOnce(iterationInterval)(campaignDomain ! RequestCampaign(crawlDaysAtOnce))
        }
  }
}
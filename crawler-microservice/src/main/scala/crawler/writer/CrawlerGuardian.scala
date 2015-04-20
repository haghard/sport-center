package crawler.writer

import akka.actor._
import akka.pattern.ask
import crawler.CrawlerMaster
import microservice.crawler._
import domain.CrawlerCampaign

import microservice.settings.CustomSettings
import ddd.amod.{ EffectlessAck, Acknowledge }
import ddd.{ PassivationConfig, AggregateRootActorFactory, CustomShardResolution }
import domain.CrawlerCampaign.{ InitCampaign, CrawlerTask, PersistCampaign, RequestCampaign }

import scala.concurrent.duration._
import scala.util.{ Failure, Success }

object CrawlerGuardian {
  def props(settings: CustomSettings) = Props(new CrawlerGuardian(settings))
}

class CrawlerGuardian private (settings: CustomSettings) extends Actor with ActorLogging {
  import ddd.Shard._
  import ddd.LocalShard._

  implicit val sys = context.system

  private val startPoint = settings.intervals.headOption

  private val daysInBatch = settings.crawler.daysInBatch //default 7
  private val iterationPeriod = settings.crawler.iterationPeriod //default 1 hours
  private val jobTimeout = settings.crawler.jobTimeout //default 60 seconds

  private val formatter = searchFormatter()

  private val crawlerMaster = context.actorOf(CrawlerMaster.props(settings), "crawler-master")

  implicit object ShardResolution extends CustomShardResolution[CrawlerCampaign]
  implicit object agFactory extends AggregateRootActorFactory[CrawlerCampaign] {
    override def inactivityTimeout: Duration = 10.minute
    override def props(pc: PassivationConfig) = Props(new CrawlerCampaign(pc))
  }

  private val campaignDomain: ActorRef = shardOf[CrawlerCampaign]

  implicit val timeout = akka.util.Timeout(10 seconds)
  implicit val ec = context.system.dispatchers.lookup("scheduler-dispatcher")

  override def preStart = {
    val start = startPoint.fold(throw new Exception("app-settings.stages prop must be defined"))
    { _._1.getStart.withZone(SCENTER_TIME_ZONE).withTime(23, 59, 59, 0).toDate }
    campaignDomain ! InitCampaign(start)
  }

  private def scheduleCampaign = {
    context.system.scheduler.scheduleOnce(10 seconds)(campaignDomain ! RequestCampaign(daysInBatch))
    context become receive
  }

  override def receive: Receive = {
    case Acknowledge(Success("OK")) | EffectlessAck(Success("OK")) =>
      campaignDomain ! RequestCampaign(daysInBatch)

    case CrawlerTask(None) ⇒
      context.system.scheduler.scheduleOnce(iterationPeriod)(campaignDomain ! RequestCampaign(daysInBatch))
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
        .onComplete {
          case Success(c) ⇒
            log.info("ChangeSet was persisted")
            scheduleCampaign
          case Failure(error) ⇒
            log.info("ChangeSet save error {}", error.getMessage)
            context become receive
            context.system.scheduler.scheduleOnce(iterationPeriod)(campaignDomain ! RequestCampaign(daysInBatch))
        }
  }
}
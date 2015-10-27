package crawler.writer

import akka.actor._
import akka.pattern.ask
import crawler.CrawlerRoot
import microservice.crawler._
import domain.CrawlerCampaign
import microservice.settings.CustomSettings
import ddd.amod.{ EffectlessAck, Acknowledge }
import ddd.{ PassivationConfig, AggregateRootActorFactory, CustomShardResolution }
import domain.CrawlerCampaign.{ InitCampaign, CrawlerTask, PersistCampaign, RequestCampaign }
import org.joda.time.DateTime

import scala.concurrent.duration._
import scala.util.{ Failure, Success }

object CrawlerGuardian {
  case class CrawlerResponse(dt: DateTime, results: List[NbaResult])

  implicit object ShardResolution extends CustomShardResolution[CrawlerCampaign]
  implicit object agFactory extends AggregateRootActorFactory[CrawlerCampaign] {
    override def inactivityTimeout = 10 minute
    override def props(pc: PassivationConfig) = Props(new CrawlerCampaign(pc))
  }

  def props(settings: CustomSettings) = Props(new CrawlerGuardian(settings))
}

class CrawlerGuardian private (settings: CustomSettings) extends Actor with ActorLogging {
  import CrawlerGuardian._
  import ddd.Shard._
  import ddd.LocalShard._

  implicit val sys = context.system

  private val startPoint = settings.intervals.headOption

  private val daysInBatch = settings.crawler.daysInBatch //default 7
  private val iterationPeriod = settings.crawler.iterationPeriod //default 1 hours
  private val jobTimeout = settings.crawler.jobTimeout //default 60 seconds

  private val formatter = searchFormatter()

  private val crawler = context.actorOf(CrawlerRoot.props(settings), "crawler-root")

  private val campaign = shardOf[CrawlerCampaign]

  implicit val timeout = akka.util.Timeout(10 seconds)
  implicit val ec = context.system.dispatchers.lookup("scheduler-dispatcher")

  override def preStart = {
    val start = startPoint.fold(throw new Exception("app-settings.stages prop must be defined")) { _._1.getStart.withZone(SCENTER_TIME_ZONE).withTime(23, 59, 59, 0).toDate }
    log.info("CrawlerGuardian")
    campaign ! InitCampaign(start)
  }

  private def scheduleCampaign = {
    context.system.scheduler.scheduleOnce(2 seconds)(campaign ! RequestCampaign(daysInBatch))
    context become receive
  }

  override def receive: Receive = {
    case Acknowledge(Success("OK")) | EffectlessAck(Success("OK")) =>
      campaign ! RequestCampaign(daysInBatch)

    case CrawlerTask(None) ⇒
      context.system.scheduler.scheduleOnce(iterationPeriod)(campaign ! RequestCampaign(daysInBatch))
      log.info("Prevent useless crawler iteration. Data is up to date")

    case CrawlerTask(Some(job)) ⇒
      log.info("Schedule crawler job up to {} date", formatter format job.endDt.toDate)
      context setReceiveTimeout jobTimeout
      crawler ! job
      context become waitForCrawler
  }

  private val waitForCrawler: Receive = {
    case _: JobFailed | _: ReceiveTimeout ⇒
      log.info("Crawler job failed cause timeout/failed")
      scheduleCampaign

    case CrawlerResponse(dt, results) ⇒
      log.info("We have received response from job [{}] - size: {}", dt, results.size)
      campaign
        .ask(PersistCampaign(dt, results))
        .mapTo[ddd.amod.Acknowledge]
        .onComplete {
          case Success(c) ⇒
            campaign ! RequestCampaign(daysInBatch)
            context become receive
          //scheduleCampaign
          case Failure(error) ⇒
            log.info("Change-set save error {}", error.getMessage)
            context become receive
            context.system.scheduler.scheduleOnce(iterationPeriod)(campaign ! RequestCampaign(daysInBatch))
        }
  }
}
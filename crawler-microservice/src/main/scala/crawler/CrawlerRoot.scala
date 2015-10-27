package crawler

import akka.actor.SupervisorStrategy.{ Directive, Resume }
import akka.actor._
import crawler.WebGetter.CrawlerException
import crawler.writer.CrawlerGuardian.CrawlerResponse
import microservice.api.MicroserviceKernel
import microservice.crawler._
import microservice.settings.CustomSettings
import org.joda.time.DateTime

object CrawlerRoot {
  /**
   * .withFallback(ConfigFactory.load("crawler.conf"))
   * @param settings
   * @return
   */
  def props(settings: CustomSettings) = Props(new CrawlerRoot(settings.teams) with FromConfigCreator)

  def props2(settings: CustomSettings) = Props(new CrawlerRoot(settings.teams) with ProgrammaticallyCreator)

}

/**
 *
 * Pool - router that creates routees as child actors and deploys them on remote nodes.
 * Each router will have its own routee instances. For example,
 * if you start a router on 3 nodes in a 10 nodes cluster you will have 30 routee actors
 * in total if the router is configured to use one instance per node.
 * The routees created by the different routers will not be shared between the routers.
 * One example of a use case for this type of router is a single master
 * that coordinate jobs and delegates the actual work to routees running
 * on other nodes in the cluster.
 */
abstract class CrawlerRoot(val teams: Seq[String]) extends Actor with ActorLogging with WebRouterCreator {
  private var nrOfRetries = 5
  private var collectedResults = List.empty[NbaResult]

  private var crawlerClient: Option[ActorRef] = None
  private var webAggregator: Option[ActorRef] = None

  private val formatter = searchFormatter()
  private def webRouter = createRouter

  override def routerNodeRole = MicroserviceKernel.CrawlerRole

  private val decider: PartialFunction[Throwable, Directive] = {
    case CrawlerException(_, cause, url) ⇒
      log.debug("WebGetter error {}, retry later {} ", cause.getMessage, url)
      Resume
  }

  override val supervisorStrategy =
    OneForOneStrategy()(decider.orElse(SupervisorStrategy.defaultStrategy.decider))

  def wait(dt: DateTime): Actor.Receive = {
    case TimeOut(urls, results) ⇒
      collectedResults = collectedResults ::: results
      nrOfRetries -= 1
      if (nrOfRetries == 0) {
        webAggregator foreach (context.stop(_))
        crawlerClient foreach (_ ! JobFailed(s"Several urls have not been achieved: ${urls.mkString(";")}"))
        crawlerClient = None
        nrOfRetries = 5
        collectedResults = Nil
        log.info("Mark job as failed after {} retry", nrOfRetries)
        context become idle
      } else {
        log.info("Retry countdown: {}", nrOfRetries)
        urls foreach { url ⇒ webRouter.tell(url, webAggregator.get) }
      }

    case r: SuccessCollected ⇒
      crawlerClient foreach (_ ! CrawlerResponse(dt, collectedResults ::: r.list))
      crawlerClient = None
      (context become idle)

  }

  def idle(): Receive = {
    case CrawlerJob(dt, urls) ⇒
      log.info("CrawlerRoot receive job {}", formatter format dt.toDate)
      crawlerClient = Option(sender())
      webAggregator = Option(context.actorOf(Collector.props(urls), "collector"))
      urls foreach (url ⇒ webRouter.tell(url, webAggregator.get))
      context become active(dt)
  }

  override def receive = idle

  private def active(dt: DateTime) = wait(dt)
}
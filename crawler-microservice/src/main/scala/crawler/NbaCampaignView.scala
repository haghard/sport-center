package crawler

import java.util.Date
import microservice.domain.State
import akka.actor.{ ActorLogging, Props }
import domain.CrawlerCampaign.CampaignPersistedEvent
import crawler.http.CrawlerMicroservice.GetLastCrawlDate

import scala.concurrent.duration._

object NbaCampaignView {

  case class LastUpdateDate(lastIterationDate: Option[Date] = None) extends State

  private val path = "nba"

  def props(dispatcher: String): Props =
    Props(new NbaCampaignView).withDispatcher(dispatcher)
}

class NbaCampaignView extends akka.persistence.PersistentView with ActorLogging {
  import NbaCampaignView._

  private var state = LastUpdateDate()

  override val autoUpdateInterval = 3 seconds

  override def viewId = "nba-campaign-view"

  override def persistenceId = path

  override def receive: Receive = {
    case q: GetLastCrawlDate              ⇒ sender() ! state
    case CampaignPersistedEvent(_, dt, _) ⇒ state = state.copy(Some(dt))
  }
}
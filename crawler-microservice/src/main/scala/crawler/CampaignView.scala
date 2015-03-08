package crawler

import akka.actor.{ ActorLogging, Props }
import crawler.CampaignView.LastUpdateDate
import crawler.http.CrawlerMicroservice.GetLastCrawlDate
import domain.CrawlCampaignAggregate
import domain.CrawlCampaignAggregate.CollectedChangeSet
import microservice.domain.State
import org.joda.time.DateTime

import scala.concurrent.duration._

object CampaignView {

  case class LastUpdateDate(lastIterationDate: Option[DateTime] = None) extends State

  def props(dispatcher: String) =
    Props(new CampaignView).withDispatcher(dispatcher)
}

private class CampaignView extends akka.persistence.PersistentView with ActorLogging {

  private var state = LastUpdateDate()

  override val autoUpdateInterval = 3 seconds

  override def viewId = "campaign-view"

  override def persistenceId = CrawlCampaignAggregate.AggregateId

  override def receive: Receive = {
    case CollectedChangeSet(dt, _) ⇒ state = state.copy(Some(dt))
    case q: GetLastCrawlDate       ⇒ sender() ! state
  }
}

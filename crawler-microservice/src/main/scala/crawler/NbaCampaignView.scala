package crawler

import java.util.Date
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.PersistenceQuery
import akka.stream.scaladsl.Sink
import microservice.crawler._
import microservice.domain.State
import akka.actor.{ Actor, ActorLogging, Props }
import domain.CrawlerCampaign.CampaignPersistedEvent
import crawler.http.CrawlerMicroservice.GetLastCrawlDate

object NbaCampaignView {
  private val campaignName = "nba"

  case class LastUpdateDate(lastIterationDate: Option[Date] = None) extends State

  def props(dispatcher: String): Props =
    Props(new NbaCampaignView(dispatcher)).withDispatcher(dispatcher)
}

class NbaCampaignView(dispatcher: String) extends Actor with ActorLogging {
  import NbaCampaignView._

  private var state = LastUpdateDate()
  private val formatter = estFormatter()

  val settings = akka.stream.ActorMaterializerSettings(context.system).withDispatcher(dispatcher)
  implicit val mat = akka.stream.ActorMaterializer(settings)(context.system)

  val journal = PersistenceQuery(context.system)
    .readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)
    .eventsByPersistenceId(campaignName, 0l, Long.MaxValue)

  journal
    .filter(_.event.isInstanceOf[CampaignPersistedEvent])
    .map { env =>
      val event = env.event.asInstanceOf[CampaignPersistedEvent]
      log.info(s"Crawler PROGRESS: ${(formatter format (event.dt))}")
      event
    }.to(Sink.actorRef[CampaignPersistedEvent](self, 'Completed)).run()

  override def receive: Receive = {
    case q: GetLastCrawlDate              ⇒ sender() ! state
    case CampaignPersistedEvent(_, dt, _) ⇒ state = state.copy(Some(dt))
    case 'Completed            => log.info("Completed")
  }
}
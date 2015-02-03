package domain.update

import akka.actor._
import domain.CrawlCampaignAggregate
import domain.CrawlCampaignAggregate.ChangeSetSaved
import domain.TeamAggregate.WriteResult
import domain.update.CampaignChangesetWriter.{ CompleteBatch, GetLastChangeSetNumber }
import microservice.crawler.{ CrawledNbaResult, Location }
import microservice.domain.Command
import streamz.akka.persistence
import streamz.akka.persistence.Event
import scala.collection.immutable.SortedSet
import scalaz.concurrent.Task
import scalaz.\/
import CrawlCampaignAggregate._
import scala.concurrent.duration._
import scalaz.stream._

object ChangeSetSubscriber {

  case class PersistChangeSet(id: Long, results: Map[String, SortedSet[WriteResult]], cb: Throwable \/ CompleteBatch ⇒ Unit)
    extends Command

  val executor = microservice.executor("updates-feed-executor", 2)

  /*def futureToTask[T](f: Future[T]): Task[T] = {
    Task async { cb =>
      f onComplete {
        case Success(v) => cb(\/-(v))
        case Failure(e) => cb(-\/(e))
      }
    }
  }*/

  implicit object Sort extends Ordering[WriteResult] {
    override def compare(x: WriteResult, y: WriteResult): Int =
      x.result.dt.compareTo(y.result.dt)
  }

  def props = Props(new ChangeSetSubscriber).withDispatcher("scheduler-dispatcher")
}

class ChangeSetSubscriber private extends Actor with ActorLogging {
  import domain.update.ChangeSetSubscriber._

  implicit val scheduler = DefaultScheduler
  implicit val logger = log
  implicit val system = context.system

  private val pullInterval = 10 seconds

  private val writer = context.system.actorOf(CampaignChangesetWriter.props, "campaign-change-set-writer")

  override def preStart() = writer ! GetLastChangeSetNumber

  private def startWith(updatesPoint: Long): (Event[Any] ⇒ Boolean) =
    event ⇒
      event.data.isInstanceOf[ChangeSetSaved] && event.sequenceNr >= updatesPoint

  private def streamWriter: scalaz.stream.Channel[Task, Event[Any], CompleteBatch] =
    scalaz.stream.io.channel { event ⇒
      Task async { cb ⇒
        val changeSet = event.data.asInstanceOf[ChangeSetSaved]
        val results = changeSet.results.flatMap { e ⇒
          WriteResult(e.homeTeam, CrawledNbaResult(e.roadTeam, e.homeScore, e.roadScore, e.dt, Location.Home)) ::
            WriteResult(e.roadTeam, CrawledNbaResult(e.homeTeam, e.homeScore, e.roadScore, e.dt, Location.Away)) :: Nil
        }

        val map = results.foldLeft(Map[String, SortedSet[WriteResult]]()) { (map, res) ⇒
          val set = map.getOrElse(res.aggregateRootId, SortedSet[WriteResult]())
          val newSet = set + res
          map + (res.aggregateRootId -> newSet)
        }

        writer ! PersistChangeSet(event.sequenceNr, map, cb)
      }
    }

  override def receive: Receive = {
    case sequenceNumber: Long ⇒
      log.info("Receive last applied Changeset: {}", sequenceNumber)
      persistence.replay(AggregateId).filter(startWith(sequenceNumber))
        .flatMap(event ⇒ Process.sleep(pullInterval) ++ Process.emit(event))
        .through(streamWriter).run.runAsync(_ ⇒ ())
  }
}
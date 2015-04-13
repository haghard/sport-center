package domain.update

import akka.actor._
import ddd.EventMessage
import domain.CrawlerCampaign.CampaignBeingPersisted

import domain.TeamAggregate.CreateResult
import microservice.domain.Command
import streamz.akka.persistence
import streamz.akka.persistence.Event
import scala.collection.immutable.SortedSet
import scalaz.concurrent.Task
import scalaz.\/
import scala.concurrent.duration._
import scalaz.stream._
import domain.update.CampaignChangeCapture.{ BatchPersisted, GetLastChangeSetNumber }

object NbaChangeDataCaptureSubscriber {

  case class PersistDataChange(id: Long, results: Map[String, SortedSet[CreateResult]],
    cb: Throwable \/ BatchPersisted ⇒ Unit) extends Command

  implicit object Sort extends Ordering[CreateResult] {
    override def compare(x: CreateResult, y: CreateResult): Int =
      x.result.dt.compareTo(y.result.dt)
  }

  private val path = "nba"

  def props: Props =
    Props(new NbaChangeDataCaptureSubscriber).withDispatcher("scheduler-dispatcher")
}

class NbaChangeDataCaptureSubscriber private extends Actor with ActorLogging {
  import NbaChangeDataCaptureSubscriber._

  implicit val scheduler = DefaultScheduler
  implicit val logger = log
  implicit val system = context.system

  private val pullInterval = 10 seconds

  private val changeCapture = context.system.actorOf(CampaignChangeCapture.props, "nba-change-capture-subscriber")

  override def preStart() = changeCapture ! GetLastChangeSetNumber

  private def streamWriter: scalaz.stream.Channel[Task, Event[Any], BatchPersisted] =
    io.channel { event ⇒
      Task async { cb ⇒
        val ev = event.data.asInstanceOf[EventMessage].event.asInstanceOf[CampaignBeingPersisted]
        val map = ev.results.foldLeft(Map[String, SortedSet[CreateResult]]()) { (map, res) ⇒
          val set = map.getOrElse(res.homeTeam, SortedSet[CreateResult]())
          val newSet = set + CreateResult(res.homeTeam, res)
          map + (res.homeTeam -> newSet)
        }
        changeCapture ! PersistDataChange(event.sequenceNr, map, cb)
      }
    }

  override def receive: Receive = {
    case sequenceNumber: Long ⇒
      log.info("Receive last applied ChangeUpdate #: {}", sequenceNumber)
      (for {
        event <- persistence.replay(path, sequenceNumber).filter(_.data.asInstanceOf[EventMessage].event.isInstanceOf[CampaignBeingPersisted]) //.filter(startWith(sequenceNumber))
        _ <- (Process.sleep(pullInterval) ++ Process.emit(event)) through streamWriter
      } yield ()).run.runAsync(_ => ())
  }
}
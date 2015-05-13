package domain.update

import akka.actor._
import domain.Domains
import microservice.domain.DomainEvent
import domain.update.NbaChangeDataCaptureSubscriber.PersistDataChange
import akka.persistence.{ PersistentActor, RecoveryCompleted, RecoveryFailure }

import scalaz.{ \/, \/- }

object CampaignChangeCapture {

  case class BatchPersisted(seqNumber: Long) extends DomainEvent
  case class ChangeSetLogicalTimeline(sn: Long, size: Int) extends DomainEvent

  object GetLastChangeSetNumber

  def props = Props(new CampaignChangeCapture).withDispatcher("scheduler-dispatcher")
}

class CampaignChangeCapture private extends PersistentActor with ActorLogging {
  import CampaignChangeCapture._

  private var sequenceNr = 1l

  //TODO: replace with comment from below
  Domains(context.system).start()

  /*
  import ddd._
  import ddd.{PassivationConfig, AggregateRootActorFactory, CustomShardResolution}
  implicit val sys = context.system
  
  import ddd.ClusteredShard._
  implicit object ShardResolution extends CustomShardResolution[NbaTeam]
  implicit object ARFactory extends AggregateRootActorFactory[NbaTeam] {
    override def props(pc: PassivationConfig) = Props(new NbaTeam(pc) {})
  }
  private val domain = ddd.Shard.shardOf[NbaTeam]
  */

  implicit val ec = context.system.dispatchers.lookup("scheduler-dispatcher")

  override def persistenceId = "campaign-change-capture"

  private var callback: Option[Throwable \/ BatchPersisted ⇒ Unit] = None

  override def receiveRecover: Receive = {
    case e: ChangeSetLogicalTimeline ⇒ updateState(e)
    case RecoveryFailure(ex)         ⇒ log.info("Recovery failure {}", ex.getMessage)
    case RecoveryCompleted           ⇒ log.info("Completely recovered. Last applied changeSet: {}", sequenceNr)
  }

  private def updateState(event: DomainEvent) = {
    event match {
      case ChangeSetLogicalTimeline(number, size) ⇒
        sequenceNr = number
        callback = None
    }
  }

  override def receiveCommand: Receive = {
    case PersistDataChange(sn, results, cb) ⇒
      persist(ChangeSetLogicalTimeline(sn, results.size)) { ev ⇒
        updateState(ev)
        callback = Some(cb)
        if (results.size > 0) {
          log.info("Schedule write changeSet {}", sn)
          Domains(context.system).tellBatchWrite(sn, results)
        } else { self ! "Done" }
      }
    case "Done"                 ⇒ for { cb ← callback } yield cb(\/-(BatchPersisted(sequenceNr)))
    case GetLastChangeSetNumber ⇒ sender() ! sequenceNr
  }
}
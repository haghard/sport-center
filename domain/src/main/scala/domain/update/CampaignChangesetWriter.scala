package domain.update

import akka.actor._
import domain.Domains
import akka.persistence.{ PersistentActor, RecoveryCompleted, RecoveryFailure }
import domain.update.ChangeSetSubscriber.PersistChangeSet
import microservice.domain.{ Command, DomainEvent }

import scalaz.{ \/, \/- }

object CampaignChangesetWriter {
  case class CompleteBatch(id: Long) extends Command
  case class ChangeSetAppliedPosition(seqNumber: Long) extends DomainEvent

  object GetLastChangeSetNumber

  def props = Props(new CampaignChangesetWriter)
    .withDispatcher("scheduler-dispatcher")
}

class CampaignChangesetWriter private extends PersistentActor with ActorLogging {
  import domain.update.CampaignChangesetWriter._

  private var currentSequenceNr = 1l

  implicit val ec = context.system.dispatchers.lookup("scheduler-dispatcher")

  private var callback: Option[Throwable \/ CompleteBatch ⇒ Unit] = None

  override def persistenceId = "campaign-change-set-writer"

  override def receiveRecover: Receive = {
    case e: ChangeSetAppliedPosition ⇒
      updateState(e)
    case RecoveryFailure(ex) ⇒
      log.info("Recovery failure {}", ex.getMessage)
    case RecoveryCompleted ⇒
      log.info("Completely recovered. Last applied changeSet: {}", currentSequenceNr)
  }

  private def updateState(event: DomainEvent) = {
    event match {
      case ChangeSetAppliedPosition(number) ⇒
        currentSequenceNr = number
        callback = None
    }
  }

  override def receiveCommand: Receive = {
    case PersistChangeSet(sequenceNr, results, cb) ⇒
      persist(ChangeSetAppliedPosition(sequenceNr)) { ev ⇒
        updateState(ev)
        callback = Some(cb)
        if (results.size > 0) {
          log.info("Schedule write changeSet {}", sequenceNr)
          Domains(context.system).tellBatchWrite(sequenceNr, results)
        } else { self ! "Done" }
      }
    case "Done" ⇒
      for { cb ← callback } yield {
        cb(\/-(CompleteBatch(currentSequenceNr)))
      }
    case GetLastChangeSetNumber ⇒
      sender() ! currentSequenceNr
  }
}
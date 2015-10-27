package domain.update

import akka.actor._
import domain.Domains
import microservice.domain.DomainEvent
import domain.update.WriterGuardian.PersistDataChange
import akka.persistence.{ PersistentActor, RecoveryCompleted }

/*
  import ddd.Shard._
  import ddd.{ PassivationConfig, AggregateRootActorFactory, CustomShardResolution }
  import ddd.ClusteredShard._
  implicit object ShardResolution extends CustomShardResolution[domain.NbaTeam]
  implicit object agFactory extends AggregateRootActorFactory[domain.NbaTeam] {
    override val inactivityTimeout = 10 minute
    override def props(pc: PassivationConfig) = Props(new domain.NbaTeam(pc))
  }
*/
object DistributedDomainWriter {
  object GetLastChangeSetOffset

  case class BatchPersisted(seqNumber: Long) extends DomainEvent
  case class BeginTransaction(sn: Long, size: Int) extends DomainEvent

  def props = Props(new DistributedDomainWriter)
    .withDispatcher("scheduler-dispatcher")
}

class DistributedDomainWriter extends PersistentActor with ActorLogging {
  import scala.concurrent.duration._
  import DistributedDomainWriter._

  implicit val ts = 5 second

  var sequenceNum = 1l
  var requestor: Option[ActorRef] = None

  override val persistenceId = "domain-writer"
  implicit val sys = context.system
  implicit val ex = context.system.dispatchers.lookup("scheduler-dispatcher")

  override def receiveRecover: Receive = {
    case e: BeginTransaction ⇒ updateState(e)
    case RecoveryCompleted ⇒
      log.info("Recovered up to change-set №{}", sequenceNum)
      //val shard = shardOf[domain.NbaTeam]
      Domains(context.system).start()
  }

  private def updateState(event: DomainEvent) = event match {
    case BeginTransaction(number, size) ⇒
      sequenceNum = number
  }

  override def onRecoveryFailure(cause: Throwable, ev: Option[Any]) = {
    log.error(cause, "Recovery failed")
    super.onRecoveryFailure(cause, ev)
  }

  override def receiveCommand: Receive = {
    case change: PersistDataChange =>
      requestor = Option(sender)
      persist(BeginTransaction(change.id, change.results.size)) { ev ⇒
        //updateState(ev)
        if (!change.results.isEmpty) {
          log.info("Schedule write change-set №{} in sharded domain", change.id)
          Domains(context.system).distributedWrite(change.id, change.results, ts)
        } else self ! change.id
      }

    case seqNum: Long ⇒
      sequenceNum = seqNum
      log.info("change-set №{} has been written atLeastOnce", seqNum)
      requestor.foreach(_ ! seqNum)
      requestor = None
    //we can say Commit !!!!

    case GetLastChangeSetOffset ⇒ sender() ! sequenceNum
  }
}
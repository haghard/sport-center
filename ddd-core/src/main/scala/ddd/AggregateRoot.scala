package ddd

import ddd.AggregateRoot.Event
import microservice.domain.DomainEvent
import akka.actor.{ ActorRef, Props, ActorLogging }
import akka.persistence.{ RecoveryCompleted, PersistentActor }

import scala.concurrent.duration._
import scala.util.{ Success, Try }
import scala.concurrent.duration.Duration

class AggregateRootNotInitializedException extends Exception

object AggregateRoot {
  type Event = DomainEvent
}

trait AggregateState {
  type AggState = PartialFunction[Event, AggregateState]
  def apply: AggState
}

abstract class AggregateRootActorFactory[A <: AggregateRoot[_]] extends BusinessEntityActorFactory[A] {
  def props(pc: PassivationConfig): Props
  def inactivityTimeout: Duration = 5.minute
}

trait AggregateRoot[T <: AggregateState] extends BusinessEntity with ActorPassivation
    with PersistentActor with ActorLogging {

  protected type StateFactory = PartialFunction[DomainEvent, T]

  protected def factory: StateFactory

  protected var replyTo: ActorRef = null
  protected var internalState: Option[T] = None
  private var lastCommandMessage: Option[CommandMessage] = None

  override def persistenceId: String = id

  override def id = self.path.name

  override def receiveCommand: Receive = {
    case env @ CommandMessage(c, uid, dt) =>
      log.info("{} - Incoming aggregate message {}", persistenceId, env.command.getClass.getSimpleName)
      lastCommandMessage = Some(env)
      replyTo = sender()
      handleCommand.applyOrElse(c, unhandled)
  }

  override def receiveRecover: Receive = {
    case em: DomainEvent =>
      updateState(new EventMessage(em))
    case RecoveryCompleted =>
      log.info("Recovered with state {}", internalState)
  }

  override def onRecoveryFailure(cause: Throwable, ev: Option[Any]) = {
    log.info("Recovery has been failed {}, with message class: {}", cause.getMessage, ev.get.getClass.getSimpleName)
    super.onRecoveryFailure(cause, ev)
  }

  def commandMessage = lastCommandMessage.get

  def handleCommand: Receive

  def initialized = internalState.isDefined

  def updateState(em: EventMessage) {
    val nextState = if (initialized) state.apply(em.event) else factory.apply(em.event)
    internalState = Option(nextState.asInstanceOf[T])
  }

  protected def init(event: DomainEvent) {
    persist(event) { message =>
      val m = new EventMessage(event)
      log.info("Initial event {} has been persisted", event.getClass.getSimpleName)
      updateState(m)
    }
  }

  protected def raise(event: DomainEvent) {
    persist(event) { message =>
      log.info("Domain-event {} has been persisted", event.getClass.getSimpleName)
      val m = new EventMessage(event)
      updateState(m)
      handle(toDomainEventMessage(m))
    }
  }

  protected def raiseDuplicate(event: DomainEvent): Unit = {
    replyTo ! ddd.amod.EffectlessAck(Success("OK"))
  }

  protected def toDomainEventMessage(persisted: EventMessage) =
    new DomainEventMessage(persisted, AggregateSnapshotId(id, lastSequenceNr))
      .withMetaData(persisted.metadata).asInstanceOf[DomainEventMessage]

  def state = if (initialized) internalState.get else throw new AggregateRootNotInitializedException

  def handle(event: DomainEventMessage) {
    acknowledgeCommandProcessed(commandMessage)
  }

  private def acknowledgeCommandProcessed(msg: Message, result: Try[Any] = Success("OK")) {
    log.info("State: {}", internalState)
    val deliveryAck = msg.deliveryAck(result)
    replyTo ! deliveryAck
    log.info(s"Delivery ack for received command ($deliveryAck)")
  }
}
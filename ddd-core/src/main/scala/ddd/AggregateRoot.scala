package ddd

import ddd.AggregateRoot.Event
import microservice.domain.DomainEvent
import akka.actor.{ ActorRef, Props, ActorLogging }
import akka.persistence.{ RecoveryFailure, RecoveryCompleted, PersistentActor }

import scala.concurrent.duration._
import scala.util.{ Success, Try }
import scala.concurrent.duration.Duration

class AggregateRootNotInitializedException extends Exception

object AggregateRoot {
  type Event = DomainEvent
}

trait AggregateState {
  type StateMachine = PartialFunction[Event, AggregateState]
  def apply: StateMachine
}

abstract class AggregateRootActorFactory[A <: AggregateRoot[_]] extends BusinessEntityActorFactory[A] {

  def props(pc: PassivationConfig): Props

  def inactivityTimeout: Duration = 1.minute
}

trait AggregateRoot[T <: AggregateState] extends BusinessEntity
    with ActorPassivation with PersistentActor with ActorLogging {

  protected type ARStateFactory = PartialFunction[DomainEvent, T]

  protected def factory: ARStateFactory

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
    case em: EventMessage   => updateState(em)
    case RecoveryCompleted  => log.info("RecoveryCompleted {}", internalState)
    case RecoveryFailure(e) => log.info("RecoveryFailure {}", e.getMessage)
  }

  /**
   * Command message being processed. Not available during recovery
   */
  def commandMessage = lastCommandMessage.get

  def handleCommand: Receive

  def initialized = internalState.isDefined

  def updateState(em: EventMessage) {
    val nextState = if (initialized) state.apply(em.event) else factory.apply(em.event)
    internalState = Option(nextState.asInstanceOf[T])
  }

  protected def init(event: DomainEvent) {
    persist(new EventMessage(event).withMetaData(commandMessage.metadataExceptDeliveryAttributes)) { message =>
      log.info("Event persisted: {}", event.getClass.getSimpleName)
      updateState(message)
    }
  }

  protected def raise(event: DomainEvent) {
    persist(new EventMessage(event).withMetaData(commandMessage.metadataExceptDeliveryAttributes)) { message =>
      log.info("Event persisted: {}", event.getClass.getSimpleName)
      updateState(message)
      handle(toDomainEventMessage(message))
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
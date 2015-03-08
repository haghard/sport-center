package domain

import java.util.Date
import akka.persistence._
import microservice.domain._
import org.joda.time.DateTime
import domain.TeamAggregate.TeamState
import akka.contrib.pattern.ShardRegion
import akka.actor.{ ActorLogging, Props }
import com.github.nscala_time.time.Imports
import com.github.nscala_time.time.Imports._
import scala.util.control.NoStackTrace
import microservice.crawler.{ CrawledNbaResult, Location, NbaResult }

object TeamAggregate {

  trait TeamMessage {
    def aggregateRootId: String
  }

  val shardName: String = "teams"

  val idExtractor: ShardRegion.IdExtractor = {
    case u: TeamMessage ⇒ (u.aggregateRootId, u)
  }

  implicit val timeOrdering = new Ordering[DateTime] {
    override def compare(x: DateTime, y: DateTime) = x.compareTo(y)
  }

  implicit object intervalOrdering extends Ordering[Interval] {
    override def compare(x: Imports.Interval, y: Imports.Interval): Int =
      x.isBefore(y) match {
        case true                  ⇒ -1
        case false if x.isEqual(y) ⇒ 0
        case false                 ⇒ 1
      }
  }

  implicit object resultOrdering extends Ordering[NbaResult] {
    override def compare(x: NbaResult, y: NbaResult) = x.dt.compareTo(y.dt)
  }

  case class TeamState(name: Option[String] = None, lastDate: Option[Date] = None,
      error: Option[Throwable] = None) extends State {
    def withName(name: String) = copy(name = Option(name))
    def withName(name: Option[String]) = copy(name = name)
    def withLastDate(dt: Date) = copy(lastDate = Option(dt))
    def withLastDate(dt: Option[Date]) = copy(lastDate = dt)
    def withError(error: Throwable) = copy(error = Option(error))
  }

  object MakeSnapshot

  case class WriteResult(val aggregateRootId: String, result: NbaResult /*CrawledNbaResult*/ ) extends Command with TeamMessage

  case class QueryTeamState(override val aggregateRootId: String) extends QueryCommand with TeamMessage
  case class QueryTeamStateByDate(override val aggregateRootId: String, dt: String) extends QueryCommand with TeamMessage
  case class QueryTeamStateLast(override val aggregateRootId: String, size: Int, location: Location.Value)
    extends QueryCommand with TeamMessage

  case class WriteAck(val aggregateRootId: String) extends DomainEvent

  case class ResultAdded(team: String, r: NbaResult) extends DomainEvent

  case class TeamCreated(val teamId: String) extends DomainEvent
  case class SnapshotCreated(name: Option[String], lastDate: Option[Date]) extends DomainEvent
  case class RecoveryError(error: Throwable) extends DomainEvent

  case class TeamAggregateState(name: Option[String], results: List[CrawledNbaResult]) extends State
  case class TeamStateSingle(name: Option[String], res: Option[NbaResult]) extends State
  case class TeamStateSet(name: Option[String], results: List[NbaResult]) extends State

  case class TestException(msg: String) extends Exception(msg) with NoStackTrace

  val message = "Journal doesn't ready for querying"

  def props = Props(new TeamAggregate)
}

//TODO: Snapshot
class TeamAggregate private (var state: TeamState = TeamState()) extends PersistentActor with ActorLogging {
  import domain.TeamAggregate._

  override def persistenceId = self.path.name

  override def receiveCommand = persistentOps

  private def persistAndAck(from: String, result: NbaResult) =
    persist(ResultAdded(from, result)) { ev ⇒
      updateState(ev)
      sender() ! WriteAck(from)
    }

  private val persistentOps: Receive = {
    case cmd @ WriteResult(team, result) ⇒
      state.lastDate.fold(persistAndAck(team, result)) { lastDate ⇒
        //Idempotent receiver
        if (result.dt after lastDate) {
          persist(ResultAdded(team, result))(updateState)
        }
        sender() ! WriteAck(team)
      }

    case "boom"                                     ⇒ throw TestException("TeamAggregate test error")
    case SaveSnapshotSuccess(metadata)              ⇒ log.info("Team {} have been restored from snapshot {}", state.name, metadata)
    case SaveSnapshotFailure(metadata, cause)       ⇒ log.info("Failure restore from snapshot {}", cause.getMessage)
    case MakeSnapshot                               ⇒ saveSnapshot(SnapshotCreated(state.name, state.lastDate))
    case PersistenceFailure(payload, seqNum, cause) ⇒ log.info("Journal fails to write a event: {}", cause.getMessage)
  }

  override def receiveRecover: Receive = {
    case event: ResultAdded                                     ⇒ updateState(event)
    case SnapshotOffer(m: SnapshotMetadata, s: SnapshotCreated) ⇒ updateState(s)
    case event @ RecoveryFailure(ex)                            ⇒ updateState(RecoveryError(ex))
    case RecoveryCompleted                                      ⇒ log.info("RecoveryCompleted for {} up to {}", persistenceId, state.lastDate)
  }

  private def updateState(e: DomainEvent) = e match {
    case event @ ResultAdded(team, result) ⇒
      state = state.withName(event.team).withLastDate(result.dt)

    case event: SnapshotCreated ⇒
      state = state.withName(event.name).withLastDate(event.lastDate)

    case RecoveryError(cause) ⇒
      log.info("{} was marked as invalid cause {}", state.name, cause.getMessage)
      state = state.withError(cause)
  }
}
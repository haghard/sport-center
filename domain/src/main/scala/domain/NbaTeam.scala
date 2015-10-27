package domain

import ddd._
import java.util.Date
import microservice.crawler.NbaResult
import microservice.domain.DomainEvent
import domain.NbaTeam.{ PersistResult, ResultCreated, TeamAggregateState }

object NbaTeam {
  sealed trait ResultEvent extends DomainEvent { val aggregateRootId: String }
  case class ResultCreated(val aggregateRootId: String, result: NbaResult) extends ResultEvent

  case class PersistResult(aggregateId: String, r: NbaResult) extends DomainCommand

  case class TeamAggregateState(name: Option[String] = None, lastDate: Option[Date] = None) extends AggregateState {
    override def apply = {
      case ResultCreated(team, result) => copy(name, Option(result.dt))
    }
  }
}

class NbaTeam(override val pc: PassivationConfig) extends AggregateRoot[TeamAggregateState] with IdempotentReceiver {

  override val factory: StateFactory = {
    case ResultCreated(team, result) ⇒
      TeamAggregateState(Some(team), Option(result.dt))
  }

  override def handleCommand: Receive = {
    case PersistResult(team, result) =>
      if (!initialized) {
        raise(ResultCreated(team, result))
      } else {
        nonDuplicate(result.dt.after(state.lastDate.get)) {
          raise(ResultCreated(team, result))
        } {
          log.info("Out of time event: [Current dt {}] - [Received dt {}]]", state.lastDate.get, result.dt)
          raiseDuplicate(ResultCreated(team, result))
        }
      }
  }
}
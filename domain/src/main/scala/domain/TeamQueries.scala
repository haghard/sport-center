package domain

import akka.actor.{ Actor, ActorLogging }
import domain.TeamAggregate.{ TeamState, _ }
import microservice.crawler.{ CrawledNbaResult, Location, NbaResult }

trait TeamQueries {
  mixin: Actor with ActorLogging { def state: TeamState } ⇒

  private val mapper: CrawledNbaResult ⇒ NbaResult =
    r ⇒
      if (r.lct == Location.Home) {
        NbaResult(state.name.get, r.homeScore, r.opponent, r.awayScore, r.dt)
      } else {
        NbaResult(r.opponent, r.homeScore, state.name.get, r.awayScore, r.dt)
      }

  protected def withQueries(receive: Receive) = receive orElse queries

  private val queries: Receive = {
    case q @ QueryTeamState(id) ⇒
      sender() ! TeamAggregateState(state.name, state.results.values.toList)

    case q @ QueryTeamStateByDate(id, dt) ⇒
      sender() ! TeamStateSingle(Some(id), state.results.get(dt)
        filter { _.lct == Location.Home }
        map { r ⇒ NbaResult(state.name.get, r.homeScore, r.opponent, r.awayScore, r.dt) })

    case q @ QueryTeamStateLast(id, size, location) ⇒
      location match {
        case Location.All ⇒
          val list = state.results.values.takeRight(size)
          sender() ! TeamStateSet(Some(id), list.map(mapper).toList)
        case Location.Home ⇒
          val results = state.results.values
            .foldRight(List[NbaResult]()) { (c, acc) ⇒
              if (c.lct == Location.Home && acc.size < size) {
                NbaResult(state.name.get, c.homeScore, c.opponent, c.awayScore, c.dt) :: acc
              } else
                acc
            }
          sender() ! TeamStateSet(Some(id), results)
        case Location.Away ⇒
          val results = state.results.values
            .foldRight(List[NbaResult]()) { (c, acc) ⇒
              if (c.lct == Location.Away && acc.size < size) {
                NbaResult(c.opponent, c.homeScore, state.name.get, c.awayScore, c.dt) :: acc
              } else {
                acc
              }
            }
          sender() ! TeamStateSet(Some(id), results)
      }
  }
}
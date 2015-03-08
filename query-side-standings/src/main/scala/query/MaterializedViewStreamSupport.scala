package query

import org.joda.time.DateTime
import microservice.crawler.NbaResult
import domain.TeamAggregate.ResultAdded
import microservice.settings.CustomSettings
import akka.actor.{ Actor, ActorLogging, ActorRef }

import scala.collection.immutable
import scalaz.concurrent.Task
import scalaz.stream._

object MaterializedViewStreamSupport {

  def viewName(name: String): String = s"materialized-view-$name"
}

trait MaterializedViewStreamSupport {
  self: Actor with ActorLogging { def settings: CustomSettings } ⇒
  import streamz.akka._
  import scalaz.stream.Process._
  import query.MaterializedViewStreamSupport._

  private lazy val executor =
    scalaz.concurrent.Strategy.Executor(microservice.executor("materialized-view-executor", 2))

  private val childViews: immutable.Map[String, ActorRef] =
    settings.stages.foldLeft(immutable.Map[String, ActorRef]()) { (map, c) ⇒
      val vName = viewName(c._1)
      val view = context.actorOf(StandingMaterializedView.props(settings), name = vName)
      log.info("{} was created", vName)
      map + (vName -> view)
    }

  private def subscriber(domainActorName: String): Process[Task, NbaResult] =
    persistence.replay(domainActorName)(context.system).map(_.data.asInstanceOf[ResultAdded].r)

  private val childViewRouter: Sink[Task, NbaResult] = io.channel(result ⇒ Task.delay {
    getChildView(new DateTime(result.dt)).fold { log.info("StandingMaterializedView wasn't found for {}", result.dt) } {
      view ⇒ view ! result
    }
  })

  private def pull() = {
    val P = emitAll(settings.teams) |> process1.lift(subscriber)
    (merge.mergeN(P)(executor)
      to childViewRouter).run.runAsync(_ ⇒ ())
  }

  override def preStart() = pull()

  /**
   *
   * @param dt
   * @return
   */
  protected def getChildView(dt: DateTime): Option[ActorRef] = {
    (for {
      (interval, intervalName) ← settings.intervals
      if interval.contains(dt)
    } yield intervalName).headOption
      .flatMap { v ⇒
        childViews.get(viewName(v))
      }
  }
}
package query

import akka.actor.Actor
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.PersistenceQuery
import akka.stream.{ ClosedShape, Graph, SourceShape }
import akka.stream.scaladsl._
import domain.TeamAggregate.ResultAdded
import microservice.crawler.NbaResultView

trait StandingStream {
  mixin: Actor =>

  import GraphDSL.Implicits._

  private def flow(teams: Map[String, Int]) = Source.fromGraph(
    GraphDSL.create() { implicit b =>
      val merge = b.add(Merge[NbaResultView](teams.size))
      teams.foreach { kv =>
        PersistenceQuery(context.system)
          .readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)
          .eventsByPersistenceId(kv._1, kv._2, Long.MaxValue)
          .collect {
            case env if (env.event.isInstanceOf[ResultAdded]) =>
              val e = env.event.asInstanceOf[ResultAdded]
              NbaResultView(e.r.homeTeam, e.r.homeScore, e.r.awayTeam, e.r.awayScore, e.r.dt, e.r.homeScoreBox, e.r.awayScoreBox)
          } ~> merge
      }
      SourceShape(merge.out)
    }
  )

  def replayGraph(teams: Map[String, Int]) = {
    GraphDSL.create() { implicit b =>
      flow(teams) ~> Sink.actorRef[NbaResultView](self, 'RefreshCompleted)
      ClosedShape
    }
  }

  /*
  def deserializer: (CassandraSource#Record, CassandraSource#Record) ⇒ Any =
    (outer, inner) ⇒
      serialization.deserialize(Bytes.getArray(inner.getBytes("message")), classOf[PersistentRepr]).get.payload

  private def fetchResult(seqNum: Long)(implicit client: CassandraSource#Client) =
    (Join[CassandraSource] left (qTeams, teamsTable, qResults(seqNum), settings.cassandra.table, settings.cassandra.keyspace))(deserializer)
      .source
      .filter(_.isInstanceOf[ResultAdded])
      .map { res =>
        val r = res.asInstanceOf[ResultAdded].r
        NbaResultView(r.homeTeam, r.homeScore, r.awayTeam, r.awayScore, r.dt, r.homeScoreBox, r.awayScoreBox)
      }

  def viewStream(seqNum: Long, interval: FiniteDuration, client: CassandraSource#Client, des: ActorRef, acc: Long, f: NbaResultView => Option[ActorRef])(implicit Mat: ActorMaterializer): Unit =
    (if (acc == 0) fetchResult(seqNum)(client) else fetchResult(seqNum)(client) via readEvery(interval))
      .grouped(Mat.settings.maxInputBufferSize)
      .map { batch =>
        batch.foreach { r =>
          f(r).fold(log.debug("MaterializedView wasn't found for {}", r.dt))(_ ! r)
        }
      }
      .to(Sink.onComplete { _ =>
        (des.ask(seqNum)(interval)).mapTo[Long].map { n =>
          context.system.scheduler.scheduleOnce(interval, new Runnable {
            override def run() = viewStream(n, interval, client, des, acc + 1l, f)
          })
        }
      }).run()(Mat)*/
}
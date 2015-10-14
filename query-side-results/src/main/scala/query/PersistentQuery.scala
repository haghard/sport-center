package query

import akka.actor.{ Status, ActorLogging, Props }
import akka.persistence.{ RecoveryCompleted, PersistentActor }
import akka.stream.{ Supervision, ActorMaterializer, ActorMaterializerSettings }
import akka.stream.scaladsl.Sink
import microservice.domain.DomainEvent
import microservice.settings.CustomSettings
import scala.collection.JavaConverters._
import com.datastax.driver.core.{ ConsistencyLevel, Row, Cluster }
import join.cassandra.CassandraSource

object PersistentQuery {
  import dsl.cassandra._

  val qTeams = for { q ← select("SELECT processor_id FROM {0}") } yield q

  def qEvents(seqNums: Map[String, Long])(r: Row) = for {
    _ ← select(s"select persistence_id, sequence_nr, message from {0} where persistence_id = ? and sequence_nr > ${seqNums(r.getString("processor_id"))} and partition_nr = 0")
    _ ← fk[java.lang.String]("persistence_id", r.getString("processor_id"))
    q ← readConsistency(ConsistencyLevel.QUORUM)
  } yield q

  case class PersistentItem(name: String, num: Long)
  case class StreamPositionsUpdated(map: Map[String, Long]) extends DomainEvent

  def fold = { (acc: Map[String, Long], line: PersistentItem) ⇒
    val prev = acc(line.name)
    if (prev < line.num) acc + (line.name -> line.num)
    else acc
  }

  def cmb: (CassandraSource#Record, CassandraSource#Record) ⇒ PersistentItem =
    (outer, inner) ⇒ {
      inner.getBytes("message")
      PersistentItem(outer.getString("processor_id"), inner.getLong("sequence_nr"))
    }

  /**
   *
   *
   */
  def props(settings: CustomSettings) =
    Props(new PersistentQuery(settings)).withDispatcher("stream-dispatcher")
}

class PersistentQuery private (settings: CustomSettings) extends PersistentActor with ActorLogging {
  import join.Join
  import PersistentQuery._
  import akka.pattern.pipe
  import scala.concurrent.duration._

  val decider: Supervision.Decider = {
    case ex ⇒
      log.error(ex, "ResultsStreamer error")
      Supervision.Stop
  }
  implicit val Mat = ActorMaterializer(ActorMaterializerSettings(context.system)
    .withDispatcher("stream-dispatcher")
    .withSupervisionStrategy(decider)
    .withInputBuffer(32, 64))(context.system)
  implicit val dispatcher = context.system.dispatchers.lookup("stream-dispatcher")

  var client: Option[CassandraSource#Client] = None
  var seqNumbers: Map[String, Long] = Map[String, Long]().withDefaultValue(0l)

  override val persistenceId = "results-streamer"

  private def createClient = Cluster.builder().addContactPointsWithPorts(List(settings.cassandra.address).asJava).build

  private def fetch = {
    if (client.isEmpty)
      client = Option(createClient)

    implicit var cl = client.get
    val future = (Join[CassandraSource] left (qTeams, "teams", qEvents(seqNumbers), settings.cassandra.table, settings.cassandra.keyspace))(cmb)
      .source
      .runWith(Sink.fold(Map[String, Long]().withDefaultValue(0l))(fold))
    future pipeTo self
  }

  override def preStart = schedule

  private def schedule = context.system.scheduler.scheduleOnce(10 seconds)(fetch)

  override def onRecoveryFailure(cause: Throwable, ev: Option[Any]) = {
    log.info("onRecoveryFailure", cause.getMessage)
    super.onRecoveryFailure(cause, ev)
  }

  override def receiveCommand = {
    case map: Map[String, Long] =>
      if (map.isEmpty) log.info("Changes haven't been found")
      else {
        persist(StreamPositionsUpdated(map)) { event =>
          log.info("Cle seqNumber {} ", event.map("cle"))
          seqNumbers = event.map
        }
      }
      schedule

    case Status.Failure(ex) =>
      log.error("Persistent failure", ex)
      if (client.isDefined) {
        client.get.close()
        client = None
      }
      schedule
    //context.system.stop(self)
  }

  override def receiveRecover = {
    case RecoveryCompleted ⇒
      log.info("RecoveryCompleted for {} up to {}", persistenceId, seqNumbers)
  }
}
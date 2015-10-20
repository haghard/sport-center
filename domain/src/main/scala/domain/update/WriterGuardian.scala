package domain.update

import akka.actor._
import scala.concurrent.duration._
import microservice.domain.Command
import domain.TeamAggregate.CreateResult
import scala.collection.immutable.SortedSet
import microservice.settings.CustomSettings
import akka.serialization.SerializationExtension
import domain.update.DistributedDomainWriter.GetLastChangeSetOffset
import akka.stream.{ Supervision, ActorMaterializerSettings, ActorMaterializer }

object WriterGuardian {
  case class PersistDataChange(id: Long, results: Map[String, SortedSet[CreateResult]]) extends Command

  def props(settings: CustomSettings) = Props(classOf[WriterGuardian], settings)
    .withDispatcher("scheduler-dispatcher")
}

class WriterGuardian private (val settings: CustomSettings) extends Actor with ActorLogging
    with ChangesStream with CassandraQueriesSupport {
  val interval = 30 seconds
  val serialization = SerializationExtension(context.system)
  val writeProcessor = context.system.actorOf(DistributedDomainWriter.props, "distributed-writer")

  val decider: Supervision.Decider = {
    case ex ⇒
      log.error(ex, "Changes fetch error")
      Supervision.stop
  }

  implicit val Mat = ActorMaterializer(ActorMaterializerSettings(context.system)
    .withDispatcher("stream-dispatcher")
    .withSupervisionStrategy(decider)
    .withInputBuffer(1, 1))(context.system)

  override def preStart() = writeProcessor ! GetLastChangeSetOffset

  override def receive: Receive = {
    case offset: Long ⇒
      log.info("Receive last applied ChangeUpdate №{}", offset)
      changesStream(offset, interval, quorumClient, writeProcessor)
  }
}
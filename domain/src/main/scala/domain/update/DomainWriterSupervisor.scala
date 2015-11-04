package domain.update

import akka.actor._
import akka.cluster.sharding.ShardRegion
import scala.concurrent.duration._
import microservice.domain.Command
import domain.TeamAggregate.CreateResult
import scala.collection.immutable.SortedSet
import microservice.settings.CustomSettings
import akka.serialization.SerializationExtension
import domain.update.DomainWriter.GetLastChangeSetOffset
import akka.stream.{ Supervision, ActorMaterializerSettings, ActorMaterializer }

object DomainWriterSupervisor {
  case class PersistDataChange(id: Long, results: Map[String, SortedSet[CreateResult]]) extends Command

  def props(settings: CustomSettings) = Props(classOf[DomainWriterSupervisor], settings)
    .withDispatcher("scheduler-dispatcher")
}

class DomainWriterSupervisor private (val settings: CustomSettings) extends Actor with ActorLogging
    with ChangesStream with CassandraQueriesSupport {
  val interval = 15 seconds
  val serialization = SerializationExtension(context.system)

  var requestor: Option[ActorRef] = None
  val writer = context.system.actorOf(DomainWriter.props, "domain-writer")

  val decider: Supervision.Decider = {
    case ex ⇒
      log.error(ex, "Changes fetch error")
      Supervision.stop
  }

  implicit val Mat = ActorMaterializer(ActorMaterializerSettings(context.system)
    .withDispatcher("stream-dispatcher")
    .withSupervisionStrategy(decider)
    .withInputBuffer(1, 1))(context.system)

  override def preStart() = {
    context watch writer
    writer ! GetLastChangeSetOffset
  }

  override def receive: Receive = {
    case offset: Long ⇒
      log.info("Last applied change-set №{}", offset)
      changesStream(offset, interval, quorumClient, writer)
  }
}
package domain.update

import akka.actor._
import akka.serialization.SerializationExtension
import domain.TeamAggregate.CreateResult
import domain.update.DistributedDomainWriter.GetLastChangeSetNumber
import microservice.domain.Command
import microservice.settings.CustomSettings
import scala.collection.immutable.SortedSet
import scala.concurrent.duration._

object WriterGuardian {

  case class PersistDataChange(id: Long, results: Map[String, SortedSet[CreateResult]]) extends Command

  def props(settings: CustomSettings) = Props(classOf[WriterGuardian], settings)
    .withDispatcher("scheduler-dispatcher")
}

class WriterGuardian private (val settings: CustomSettings) extends Actor with ActorLogging with ChangesJournalSupport {
  val writeInterval = 10 seconds
  implicit val t = akka.util.Timeout(writeInterval)

  val serialization = SerializationExtension(context.system)
  val writeProcessor = context.system.actorOf(DistributedDomainWriter.props, "distributed-writer")

  override def preStart() = writeProcessor ! GetLastChangeSetNumber

  override def receive: Receive = {
    case seqNum: Long ⇒
      log.info("Receive last applied ChangeUpdate №{}", seqNum)
      process(seqNum, newClient)
  }
}
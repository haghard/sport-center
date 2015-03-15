package domain

import akka.actor.ActorDSL._
import akka.actor.Extension
import akka.actor._
import akka.persistence.PersistentActor
import akka.util.Timeout
import domain.TeamAggregate.{ CreateResult, WriteAck }
import microservice.domain._

import scala.collection.immutable.SortedSet
import scala.concurrent.forkjoin.ThreadLocalRandom
import scala.concurrent.{ ExecutionContext, Future }
import scalaz.\/
import scalaz.\/._
import scala.concurrent.duration._

object Domains extends ExtensionKey[Domains] {

  trait SkippedCommandValidator[T] extends CommandValidator[T] {
    self: PersistentActor with ActorLogging ⇒

    def failureRate: Double

    private val random = ThreadLocalRandom.current

    private def shouldSkip(rate: Double) =
      random.nextDouble() > rate

    abstract override def validate(cmd: Command): String \/ (Long, T) = {
      if (!shouldSkip(failureRate)) {
        log.info(s"Skip {}", cmd)
        left("Skip message")
      } else super.validate(cmd)
    }
  }

  val BatchWriteComplete = "Done"
}

class Domains(protected val system: ExtendedActorSystem) extends Extension
    with Sharding {

  import Domains._

  /**
   *
   * @param command
   * @param sender
   * @tparam T
   * @return
   */
  def tellQuery[T <: QueryCommand](command: T)(implicit sender: ActorRef) =
    tellEntry(command)

  /**
   *
   * @param command
   * @param timeout
   * @param sender
   * @param ec
   * @tparam T
   * @return
   */
  def askQuery[T <: State](command: QueryCommand)(implicit timeout: Timeout, sender: ActorRef, ec: ExecutionContext): Future[T] =
    askEntry(command)

  def tellBatchWrite[T <: State](seqNumber: Long, results: Map[String, SortedSet[CreateResult]])(implicit sender: ActorRef, factory: ActorRefFactory, ec: ExecutionContext) = {

    def atLeastOnceWriter(replyTo: ActorRef, seqNumber: Long, results: Map[String, SortedSet[CreateResult]])(implicit factory: ActorRefFactory, ec: ExecutionContext) =
      actor(new Act {
        val size = results.size
        var respNumber = 0
        context.setReceiveTimeout(5 seconds)
        become {
          case ReceiveTimeout ⇒
            system.log.info("Redeliver changeset {}. Cause expected {} actual {}", seqNumber, size, respNumber)
            respNumber = 0
            for { (k, orderedTeamResults) ← results } yield {
              orderedTeamResults.foreach(r ⇒ writeEntry(r)(self))
            }
          case event: WriteAck ⇒
            respNumber += 1
            if (respNumber == size) {
              replyTo ! BatchWriteComplete
              system.log.info("Changeset {} has been applied", seqNumber)
              context stop self
            }
        }
      })

    implicit val c = atLeastOnceWriter(sender, seqNumber, results)
    for { (k, orderedTeamResults) ← results } yield {
      orderedTeamResults foreach (r ⇒ writeEntry(r)(c))
    }
  }

  /**
   *
   * @param command
   * @param sender
   * @tparam T
   * @return
   */
  def tellWrite[T <: Command](command: T)(implicit sender: ActorRef) =
    writeEntry(command)

  /**
   *
   * @return
   */
  override protected def props: Props = TeamAggregate.props

  /**
   *
   * @return
   */
  override protected def shardCount: Int = 6

  /**
   *
   * @return
   */
  override protected def typeName: String = "teams"
}

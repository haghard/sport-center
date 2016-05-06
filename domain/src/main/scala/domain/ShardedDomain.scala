package domain

import akka.actor.ActorDSL._
import akka.actor._
import akka.cluster.sharding.ShardRegion
import akka.persistence.PersistentActor
import akka.util.Timeout
import domain.TeamAggregate.{ CreateResult, WriteAck }
import microservice.domain._

import scala.collection.immutable.SortedSet
import scala.concurrent.forkjoin.ThreadLocalRandom
import scala.concurrent.{ Promise, ExecutionContext, Future }
import scalaz.\/
import scalaz.\/._
import scala.concurrent.duration._

object ShardedDomain extends ExtensionKey[ShardedDomain] {

  trait SkippedCommandValidator[T] extends CommandValidator[T] {
    self: PersistentActor with ActorLogging ⇒

    def failureRate: Double

    private val random = ThreadLocalRandom.current

    private def shouldSkip(rate: Double) =
      random.nextDouble() > rate

    abstract override def validate(cmd: Command): String \/ (Long, T) = {
      if (!shouldSkip(failureRate)) {
        log.info("Skip {}", cmd)
        left("Skip message")
      } else super.validate(cmd)
    }
  }
}

class ShardedDomain(protected val system: ExtendedActorSystem) extends Extension with Sharding {

  def showRegions(implicit timeout: Timeout) = showLocalRegions

  def tellQuery[T <: QueryCommand](command: T)(implicit sender: ActorRef) =
    tellEntry(command)

  def askQuery[T <: State](command: QueryCommand)(implicit timeout: Timeout, sender: ActorRef, ec: ExecutionContext): Future[T] =
    askEntry(command)

  def distributedWrite[T <: State](seqNumber: Long, results: Map[String, SortedSet[CreateResult]],
    timeout: FiniteDuration)(implicit sender: ActorRef, factory: ActorRefFactory, ec: ExecutionContext) = {

    def atLeastOnceWriter(replyTo: ActorRef, seqNumber: Long, results: Map[String, SortedSet[CreateResult]])(implicit factory: ActorRefFactory, ec: ExecutionContext) =
      actor(new Act {
        val size = results.size
        var respNumber = 0
        (context setReceiveTimeout timeout)
        become {
          case ReceiveTimeout ⇒
            system.log.info("Redelivery for changeset №{}. Cause expected {} actual {}", seqNumber, size, respNumber)
            respNumber = 0
            for { (k, orderedTeamResults) ← results } yield {
              orderedTeamResults.foreach(r ⇒ writeEntry(r)(self))
            }
          case event: WriteAck ⇒
            respNumber += 1
            if (respNumber == size) {
              replyTo ! seqNumber
              context stop self
            }
        }
      })

    implicit val c = atLeastOnceWriter(sender, seqNumber, results)
    for {
      (k, orderedTeamResults) ← results
    } yield { orderedTeamResults foreach (r ⇒ writeEntry(r)(c)) }
  }

  def tellWrite[T <: Command](command: T)(implicit sender: ActorRef) =
    writeEntry(command)

  def gracefulShutdown(implicit timeout: FiniteDuration, factory: ActorRefFactory, ec: ExecutionContext): Future[Unit] = {
    val p = Promise[Unit]()
    actor(new Act {
      context watch shardRegion
      context setReceiveTimeout timeout
      shardRegion ! ShardRegion.GracefulShutdown
      become {
        case Terminated(`shardRegion`) => p.success(())
        case ReceiveTimeout            => p.failure(new Exception("Timeout to stop local shard region"))
      }
    })
    p.future
  }

  override protected def props: Props = TeamAggregate.props

  override protected def shardCount = 10

  override protected val name = "teams"
}
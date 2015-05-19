package cassandra

import akka.actor._
import com.typesafe.config.ConfigFactory
import akka.actor.SupervisorStrategy.Stop
import cassandra.SparkJobManager.StandingBatchJobSubmit

object SparkGuardian {
  def props: Props =
    Props(new SparkGuardian).withDispatcher("scheduler-dispatcher")
}

class SparkGuardian extends Actor with ActorLogging {

  override val supervisorStrategy = OneForOneStrategy() {
    case reason: ActorInitializationException =>
      log.info("Exception {}", reason.getMessage)
      Stop
    case reason: Exception =>
      log.info("Exception {}", reason.getMessage)
      Stop
  }

  override def receive: Receive = {
    case job: StandingBatchJobSubmit =>
      context.actorOf(SparkJobManager.props(ConfigFactory.load("db.conf"))) forward job
  }
}
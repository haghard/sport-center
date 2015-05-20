package cassandra

import org.joda.time.Interval
import microservice.domain.State
import com.typesafe.config.Config
import cassandra.jobs.{ PlayOffStanding, Driver, SeasonStanding }
import akka.actor.{ Actor, ActorLogging, Props }
import microservice.http.RestService.{ BasicHttpRequest, ResponseBody }
import scala.collection.mutable

object SparkJobManager {
  sealed trait JobManagerProtocol
  case class StreamJobSubmit(job: String) extends JobManagerProtocol
  case class StandingBatchJobSubmit(url: String, stage: String, teamConf: mutable.HashMap[String, String], interval: Interval)
    extends JobManagerProtocol with BasicHttpRequest

  case class Standing(team: String = "", hw: Int = 0, hl: Int = 0, aw: Int = 0, al: Int = 0, w: Int = 0, l: Int = 0) extends Serializable

  trait SparkJobView extends State with ResponseBody

  case class SeasonStandingView(count: Int = 0, west: List[Standing] = List.empty, east: List[Standing] = List.empty) extends SparkJobView

  case class PlayoffStandingView(count: Int = 0, table: List[String]) extends SparkJobView

  def props(config: Config): Props = Props(new SparkJobManager(config)).withDispatcher("scheduler-dispatcher")
}

class SparkJobManager private (override val config: Config) extends Actor with Driver with ActorLogging {
  import SparkJobManager._

  val Season = "season"
  val PlayOff = "playoff"

  override def receive: Receive = {
    case StandingBatchJobSubmit(_, stage, teamConf, interval) =>
      val responder = sender()
      log.info(s"Start spark batch job [$stage]")

      val result = if (stage.contains(Season)) {
        submit(config, MapReduce[SeasonStanding], teamConf, interval)
      } else if (stage.contains(PlayOff)) {
        submit(config, MapReduce[PlayOffStanding], teamConf, interval)
      }

      responder ! result
      context.stop(self)
  }
}
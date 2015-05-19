package cassandra.jobs

import akka.actor.ActorLogging
import com.typesafe.config.Config
import org.joda.time.Interval
import scala.collection.mutable

trait Driver {
  mixin: ActorLogging =>

  def config: Config

  def submit[T](config: Config, job: BatchJob[T], teamConf: mutable.HashMap[String, String], interval: Interval): T = {
    log.info(s"Executing job ${job.name}")
    val result = job.execute(config, teamConf, interval)
    log.info(s"Spark batch job ${job.name} finished")
    result
  }
}

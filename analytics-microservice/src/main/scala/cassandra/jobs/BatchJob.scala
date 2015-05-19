package cassandra.jobs

import com.typesafe.config.Config
import org.joda.time.Interval
import scala.collection.mutable

trait BatchJob[T] {

  def name: String

  def execute(config: Config, teamConf: mutable.HashMap[String, String], interval: Interval): T
}

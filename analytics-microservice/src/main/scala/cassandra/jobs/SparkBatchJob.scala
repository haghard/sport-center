package cassandra.jobs

import com.typesafe.config.Config
import org.apache.spark.{ SparkConf, SparkContext }
import org.joda.time.Interval
import scala.collection.mutable

trait SparkBatchJob[T] {

  def name: String

  protected def createSpark(config: Config, host: String) =
    new SparkContext(new SparkConf()
      .setAppName(name)
      .set("spark.driver.allowMultipleContexts", "true")
      .set("spark.cassandra.connection.host", host)
      .set("spark.cassandra.connection.timeout_ms", "5000")
      .setMaster(config.getString("spark.master")))

  def execute(config: Config, teamConf: mutable.HashMap[String, String], timeFilter: Interval): T
}

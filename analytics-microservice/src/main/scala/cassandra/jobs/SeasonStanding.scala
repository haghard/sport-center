package cassandra.jobs

import cassandra._
import com.typesafe.config.Config
import microservice.crawler.NbaResult
import org.apache.spark.rdd.RDD
import org.joda.time.Interval
import scala.collection.mutable
import org.apache.spark.{ SparkConf, SparkContext }
import cassandra.SparkJobManager.{ Standing, SeasonStandingView }

//val rr = rdd.aggregateByKey(List[NbaResult]())((acc, rec) => rec :: acc, (_ ::: _))
//val rr = rdd.map(r => (r._1, 1)).reduceByKey(_ + _)
//.aggregateByKey(0l)((c, rec) => 1l, (_ + _))

class SeasonStanding extends BatchJob[SeasonStandingView] {

  override def name = getClass.getSimpleName

  override def execute(config: Config, teamConf: mutable.HashMap[String, String], interval: Interval): SeasonStandingView = {

    val seeds = config.getString("db.cassandra.seeds")
    val host = seeds.split(",")
    val CassandraHost = host(0)

    val sc = new SparkContext(new SparkConf()
      .setAppName(name)
      .set("spark.cassandra.connection.host", CassandraHost)
      .setMaster("local[2]"))

    try {
      val rdd: RDD[NbaResult] = sc.cassandraRdd(teamConf.keySet, interval)

      val result = rdd.flatMap { r =>
        if (r.homeScore > r.awayScore) List((r.homeTeam, "hw"), (r.awayTeam, "al"))
        else List((r.homeTeam, "hl"), (r.awayTeam, "aw"))
      }.aggregateByKey(Standing())(
        (s: Standing, rec: String) => rec match {
          case "hw" => s.copy(hw = s.hw + 1, w = s.w + 1)
          case "hl" => s.copy(hl = s.hl + 1, l = s.l + 1)
          case "aw" => s.copy(aw = s.aw + 1, w = s.w + 1)
          case "al" => s.copy(al = s.al + 1, l = s.l + 1)
        }, { (f, s) => f.copy(f.team, f.hw + s.hw, f.hl + s.hl, f.aw + s.aw, f.al + s.al, f.w + s.w, f.l + s.l) }).cache()

      val array = (result.collect() map { kv => kv._2.copy(team = kv._1) }).sortWith(_.w > _.w)

      val (west, east) = array.toList.partition { item ⇒
        teamConf(item.team) match {
          case "west" ⇒ true
          case "east" ⇒ false
        }
      }

      SeasonStandingView(west.size + east.size, west, east)
    } catch {
      case e: Exception => throw e
    } finally {
      sc.stop()
    }
  }
}
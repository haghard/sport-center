package cassandra.jobs

import cassandra._
import java.util.Date
import scala.collection.mutable
import org.apache.spark.rdd.RDD
import com.typesafe.config.Config
import microservice.crawler.NbaResult
import org.joda.time.{ DateTime, Interval }
import com.datastax.spark.connector.CassandraRow
import cassandra.SparkJobManager.{ Standing, SeasonStandingView }

class SeasonStanding extends SparkBatchJob[SeasonStandingView] {

  override def name = getClass.getSimpleName

  override def execute(config: Config, teamConf: mutable.HashMap[String, String], timeFilter: Interval): SeasonStandingView = {
    val seeds = config.getString("db.cassandra.seeds")
    val host = seeds.split(",")

    val sc = createSpark(config, host(0))

    try {
      val rdd: RDD[CassandraRow] = sc.cassandraRdd(config).cache()

      val array = rdd.mapPartitionsWithIndex { (index, rows) =>
        for {
          row <- rows
          key = row.get[String]("processor_id")

          if (row.get[String]("marker") == "A" && teamConf.keySet.contains(key))
          event = tryDeserialize(row.getBytes("message").array(), endPos, 5)

          if (timeFilter.contains(new DateTime(event.getResult.getTime).withZone(microservice.crawler.SCENTER_TIME_ZONE)))
        } yield {
          val r = event.getResult
          NbaResult(r.getHomeTeam, r.getHomeScore, r.getAwayTeam, r.getAwayScore, new Date(r.getTime))
        }
      }.flatMap { r =>
        if (r.homeScore > r.awayScore) List((r.homeTeam, "hw"), (r.awayTeam, "al"))
        else List((r.homeTeam, "hl"), (r.awayTeam, "aw"))
      }.aggregateByKey(Standing())(
        (s: Standing, rec: String) => rec match {
          case "hw" => s.copy(hw = s.hw + 1, w = s.w + 1)
          case "hl" => s.copy(hl = s.hl + 1, l = s.l + 1)
          case "aw" => s.copy(aw = s.aw + 1, w = s.w + 1)
          case "al" => s.copy(al = s.al + 1, l = s.l + 1)
        }, { (f, s) => f.copy(f.team, f.hw + s.hw, f.hl + s.hl, f.aw + s.aw, f.al + s.al, f.w + s.w, f.l + s.l) })
        .collect()
        .map { kv => kv._2.copy(team = kv._1) }
        .sortWith(_.w > _.w)

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
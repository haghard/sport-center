package cassandra.jobs

import java.util.Date

import cassandra._
import cassandra.SparkJobManager.PlayoffStandingView
import com.datastax.spark.connector.CassandraRow
import com.typesafe.config.Config
import microservice.crawler.NbaResult
import org.apache.spark.rdd.RDD
import org.joda.time.{ DateTime, Interval }
import scala.collection.immutable.TreeMap
import scala.collection.mutable

class PlayOffStanding extends SparkBatchJob[PlayoffStandingView] {

  override def name: String = getClass.getSimpleName

  private val first = List.range(1, 9).map(_ + ". First round")
  private val second = List.range(1, 5).map(_ + ". Conference semifinal")
  private val semifinal = List.range(1, 3).map(_ + ". Conference final")
  private val stageNames = first ::: second ::: semifinal ::: List("Final. ")

  override def execute(config: Config, teamConf: mutable.HashMap[String, String], timeFilter: Interval): PlayoffStandingView = {
    val seeds = config.getString("db.cassandra.seeds")
    val host = seeds.split(",")
    val sc = createSpark(config, host(0))

    try {
      val rdd: RDD[CassandraRow] = sc.cassandraRdd(config).cache()

      val table = rdd.mapPartitionsWithIndex { (index, rows) =>
        for {
          row <- rows
          key = row.get[String]("processor_id")

          if (row.get[String]("marker") == "A" && teamConf.keySet.contains(key))
          event = tryDeserialize(row.getBytes("message").array(), endPos, 5)

          if (timeFilter.contains(new DateTime(event.getResult.getTime).withZone(microservice.crawler.SCENTER_TIME_ZONE)))
        } yield {
          val r = event.getResult
          val key = Set(r.getHomeTeam, r.getAwayTeam)
          (key, NbaResult(r.getHomeTeam, r.getHomeScore, r.getAwayTeam, r.getAwayScore, new Date(r.getTime)))
        }
      }.aggregateByKey(TreeMap[Date, String]())(
        (round, r) => if (r.homeScore > r.awayScore) round + (r.dt -> s"[${r.homeScore}:${r.awayScore} ${r.homeTeam} wins]")
        else round + (r.dt -> s"[${r.homeScore}:${r.awayScore} ${r.awayTeam} wins]"),
        (m0, m1) => m0 ++ m1)
        .map(kv => (kv._2.lastKey, kv._1.head + "-" + kv._1.last + "  " + kv._2.values.mkString(", ")))
        .sortByKey()
        .map(_._2)
        .collect()

      val r = (stageNames zip table).map { kv => s"${kv._1} ${kv._2}" }
      PlayoffStandingView(r.size, r.toList)
    } finally {
      sc.stop()
    }
  }
}
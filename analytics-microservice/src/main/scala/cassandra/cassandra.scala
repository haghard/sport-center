import java.util.Date
import com.datastax.spark.connector._
import domain.formats.DomainEventFormats.ResultAddedEvent
import microservice.crawler.NbaResult

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.joda.time.{ Interval, DateTime }

import scala.annotation.tailrec
import cassandra.jobs.BatchJob
import scala.reflect.ClassTag

package object cassandra {

  object MapReduce {
    def apply[J <: BatchJob[_]: ClassTag] = {
      implicitly[ClassTag[J]].runtimeClass.newInstance().asInstanceOf[J]
    }
  }

  //Protobuf message len in serialized message
  val len = 6 + 28

  /**
   * We know start byte position, but don't know end position
   * @param bs
   * @param l
   * @param limit
   * @return
   */
  @tailrec
  private def tryDeserialize(bs: Array[Byte], l: Int, limit: Int): ResultAddedEvent = {
    if (limit > 0) {
      try {
        val protocBody = java.util.Arrays.copyOfRange(bs, 6, l)
        ResultAddedEvent.parseFrom(protocBody)
      } catch {
        case e: Exception => tryDeserialize(bs, l + 1, limit - 1)
      }
    } else {
      println("Deserializer error " + bs.deep.mkString(","))
      ResultAddedEvent.getDefaultInstance
    }
  }

  implicit class JournalSparkContext(context: SparkContext) {
    val keyspace = context.getConf.get("spark.cassandra.journal.keyspace", "sport_center")
    val table = context.getConf.get("spark.cassandra.journal.table", "sport_center_journal")

    def cassandraRdd(teams: scala.collection.Set[String], interval: Interval): RDD[NbaResult] =
      context.cassandraTable(keyspace, table).select("processor_id", "marker", "message")
        .mapPartitionsWithIndex { (index, rows) =>
          for {
            row <- rows
            key = row.get[String]("processor_id")

            if (row.get[String]("marker") == "A" && teams.contains(key))
            event = tryDeserialize(row.getBytes("message").array(), len, 5)

            if (interval.contains(new DateTime(event.getResult.getTime).withZone(microservice.crawler.SCENTER_TIME_ZONE)))
          } yield {
            val r = event.getResult
            NbaResult(r.getHomeTeam, r.getHomeScore, r.getAwayTeam, r.getAwayScore, new Date(r.getTime))
          }
        }
  }
}
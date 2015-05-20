import cassandra.jobs.SparkBatchJob
import com.datastax.spark.connector._
import com.typesafe.config.Config
import domain.formats.DomainEventFormats.ResultAddedEvent
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

import scala.annotation.tailrec
import scala.reflect.ClassTag

package object cassandra {

  object MapReduce {
    def apply[J <: SparkBatchJob[_]: ClassTag] = {
      implicitly[ClassTag[J]].runtimeClass.newInstance().asInstanceOf[J]
    }
  }

  //Protobuf message len in serialized message
  val startPos = 6
  val endPos = startPos + 28

  /**
   * We know start byte position, but don't know end position
   */
  @tailrec
  def tryDeserialize(bs: Array[Byte], l: Int, limit: Int): ResultAddedEvent = {
    if (limit > 0) {
      try {
        val protocBody = java.util.Arrays.copyOfRange(bs, startPos, l)
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

    def cassandraRdd(config: Config): RDD[CassandraRow] = {
      val keyspace = config.getString("spark.cassandra.journal.keyspace")
      val table = config.getString("spark.cassandra.journal.table")
      context.cassandraTable(keyspace, table).select("processor_id", "marker", "message")
    }
  }

  object levenshtein {

    import scala.math._

    def minimum(i1: Int, i2: Int, i3: Int) = min(min(i1, i2), i3)

    def distance(s1: String, s2: String) = {
      val dist = Array.tabulate(s2.length + 1, s1.length + 1) { (j, i) =>
        if (j == 0) {
          i
        } else if (i == 0) {
          j
        } else {
          0
        }
      }

      for (j <- 1 to s2.length; i <- 1 to s1.length) {
        dist(j)(i) = if (s2(j - 1) == s1(i - 1)) {
          dist(j - 1)(i - 1)
        } else {
          minimum(dist(j - 1)(i) + 1, dist(j)(i - 1) + 1, dist(j - 1)(i - 1) + 1)
        }
      }

      dist(s2.length)(s1.length)
    }

    /*def main(args: Array[String]): Unit = {
      printDistance("kitten", "sitting")
      printDistance("rosettacode", "raisethysword")
    }*/

    def printDistance(s1: String, s2: String) = println("%s -> %s : %d".format(s1, s2, distance(s1, s2)))
  }

}
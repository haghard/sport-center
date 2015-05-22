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
}
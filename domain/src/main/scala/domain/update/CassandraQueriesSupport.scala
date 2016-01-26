package domain.update

import akka.actor.Actor
import akka.stream._
import akka.stream.scaladsl._
import com.datastax.driver.core._
import join.cassandra.CassandraSource
import microservice.settings.CustomSettings

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{ Duration, FiniteDuration }
import scala.collection.JavaConverters._

trait CassandraQueriesSupport {
  mixin: Actor {
    def settings: CustomSettings
  } =>

  implicit val ctx = context.system.dispatchers.lookup("stream-dispatcher")

  def cassandraClient(cl: ConsistencyLevel): CassandraSource#Client = {
    val qs = new QueryOptions()
      .setConsistencyLevel(cl)
      .setFetchSize(500)
    Cluster.builder()
      .addContactPointsWithPorts(List(settings.cassandra.address).asJava)
      .withQueryOptions(qs)
      .build
  }

  def queryByKey(journal: String) = s"""
   |SELECT * FROM ${journal} WHERE
   |        persistence_id = ? AND
   |        partition_nr = ? AND
   |        sequence_nr > ?
 """.stripMargin

}
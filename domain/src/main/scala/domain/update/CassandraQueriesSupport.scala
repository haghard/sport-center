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

  implicit val ex = context.system.dispatchers.lookup("stream-dispatcher")

  private case class Tick()

  def readEvery[T](interval: FiniteDuration)(implicit ex: ExecutionContext) = {
    Flow() { implicit b =>
      import FlowGraph.Implicits._
      val zip = b.add(ZipWith[T, Tick.type, T](Keep.left).withAttributes(Attributes.inputBuffer(1, 1)))
      Source(Duration.Zero, interval, Tick) ~> zip.in1
      (zip.in0, zip.out)
    }
  }

  def cassandraClient(cl: ConsistencyLevel): CassandraSource#Client = {
    val qs = new QueryOptions()
      .setConsistencyLevel(cl)
      .setFetchSize(500)
    Cluster.builder()
      .addContactPointsWithPorts(List(settings.cassandra.address).asJava)
      .withQueryOptions(qs)
      .build
  }
}
package domain

import akka.actor.Props
import akka.testkit.ImplicitSender
import ddd.{ PassivationConfig, AggregateRootActorFactory, CustomShardResolution }
import domain.NbaTeam.PersistResult
import microservice.crawler.NbaResult
import org.joda.time.DateTime
import org.scalatest.{ BeforeAndAfterAll, WordSpecLike, MustMatchers }

import scala.util.Success

class LocalNbaTeamShardSpec extends MongoMockSpec(MongoMockSpec.config(MongoMockSpec.freePort))
    with WordSpecLike
    with MustMatchers
    with BeforeAndAfterAll with ImplicitSender {

  import ddd.amod._
  import ddd.Shard._
  import ddd.LocalShard._

  "LocalDomain" must {
    "do at-most-once-delivery ack after persist a linearly progressing timeline results" in {
      val host = "okc"

      implicit object ShardResolution extends CustomShardResolution[NbaTeam]

      implicit object arFactory extends AggregateRootActorFactory[NbaTeam] {
        override def props(pc: PassivationConfig) = Props(new NbaTeam(pc))
      }

      val localDomain = shardOf[NbaTeam]

      localDomain ! PersistResult(host, NbaResult(host, 121, "hou", 91, DateTime.now().toDate))
      expectMsg(Acknowledge(Success("OK")))
      localDomain ! PersistResult(host, NbaResult(host, 89, "atl", 95, DateTime.now().plusDays(1).toDate))
      expectMsg(Acknowledge(Success("OK")))
      system.stop(localDomain)
      Thread.sleep(1000) //for stop guaranty
    }
  }

  "LocalDomain" must {
    "do at-most-once-delivery ack after persist nonlinear progressing timeline results" in {
      val host = "den"
      val dt = DateTime.now()

      implicit object ShardResolution extends CustomShardResolution[NbaTeam]

      implicit object arFactory extends AggregateRootActorFactory[NbaTeam] {
        override def props(pc: PassivationConfig) = Props(new NbaTeam(pc))
      }

      val localDomain = shardOf[NbaTeam]

      localDomain ! PersistResult(host, NbaResult(host, 121, "hou", 91, dt.minusDays(2).toDate))
      expectMsg(Acknowledge(Success("OK")))
      localDomain ! PersistResult(host, NbaResult(host, 89, "atl", 95, dt.minusDays(1).toDate))
      expectMsg(Acknowledge(Success("OK")))
      localDomain ! PersistResult(host, NbaResult(host, 95, "sas", 98, dt.toDate))
      expectMsg(Acknowledge(Success("OK")))
      localDomain ! PersistResult(host, NbaResult(host, 78, "mia", 125, dt.minusDays(5).toDate))
      expectMsg(ddd.amod.EffectlessAck(Success("OK")))
      system.stop(localDomain)
      Thread.sleep(1000) //for stop guaranty
    }
  }

  "LocalDomain" must {
    "recover after passivation" in {
      import scala.concurrent.duration._

      val host = "cle"
      val dt = DateTime.now()
      val inactivityTimeout0 = 10 seconds

      implicit object ShardResolution extends CustomShardResolution[NbaTeam]

      implicit object arFactory extends AggregateRootActorFactory[NbaTeam] {
        override def inactivityTimeout: Duration = inactivityTimeout0
        override def props(pc: PassivationConfig) = Props(new NbaTeam(pc))
      }

      val localDomain = shardOf[NbaTeam]
      localDomain ! PersistResult(host, NbaResult(host, 121, "hou", 91, dt.minusDays(1).toDate))
      expectMsg(Acknowledge(Success("OK")))

      Thread.sleep((inactivityTimeout0 + 3.seconds).toMillis)
      localDomain ! PersistResult(host, NbaResult(host, 111, "atl", 96, dt.toDate))
      expectMsg(Acknowledge(Success("OK")))
    }
  }
}
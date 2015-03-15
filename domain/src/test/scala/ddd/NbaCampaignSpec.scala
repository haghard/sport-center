package domain

import java.util.concurrent.Executors

import akka.actor.Props
import com.google.common.util.concurrent.ThreadFactoryBuilder
import ddd.{ PassivationConfig, AggregateRootActorFactory, CustomShardResolution }
import streamz.akka.persistence
import scala.util.Success
import ddd.amod.{ EffectlessAck, Acknowledge }
import org.joda.time.DateTime
import domain.CrawlerCampaign._
import microservice.crawler.{ NbaResult, CrawlerJob }
import akka.testkit.ImplicitSender
import org.scalatest.{ BeforeAndAfterAll, MustMatchers, WordSpecLike }

import scalaz.concurrent.Task
import scalaz.stream.Sink

class NbaCampaignSpec extends MongoMockSpec(MongoMockSpec.config(MongoMockSpec.freePort))
    with WordSpecLike
    with MustMatchers
    with BeforeAndAfterAll with ImplicitSender {

  import ddd.Shard._
  import ddd.LocalShard._

  implicit object ShardResolution extends CustomShardResolution[CrawlerCampaign]

  implicit object arFactory extends AggregateRootActorFactory[CrawlerCampaign] {
    override def props(pc: PassivationConfig) = Props(new CrawlerCampaign(pc))
  }

  "NbaCampaign" must {
    "request first incorrect usage" in {
      val startDate = new DateTime().withZone(microservice.crawler.SCENTER_TIME_ZONE)
        .withDate(2012, 10, 29).withTime(23, 59, 59, 0)

      val campaign = shardOf[CrawlerCampaign]

      campaign ! PersistCampaign(startDate.plusDays(2), List())
      expectMsgClass(classOf[CampaignInitializationError])
      system.stop(campaign)
      Thread.sleep(1000)
    }
  }

  "NbaCampaign" must {
    "init/request/persist and init again" in {

      val urls = "http://www.nba.com/gameline/20121030/" ::
        "http://www.nba.com/gameline/20121031/" ::
        "http://www.nba.com/gameline/20121101/" :: Nil

      val startDate = new DateTime().withZone(microservice.crawler.SCENTER_TIME_ZONE)
        .withDate(2012, 10, 29).withTime(23, 59, 59, 0)

      val campaign = shardOf[CrawlerCampaign]

      campaign ! InitCampaign(startDate.toDate)
      expectMsg(Acknowledge(Success("OK")))

      campaign ! RequestCampaign(3)
      val newDate = startDate.plusDays(3)
      expectMsg(CrawlerTask(Some(CrawlerJob(newDate, urls))))

      //.....crawler job ...

      campaign ! PersistCampaign(newDate, List())
      expectMsg(Acknowledge(Success("OK")))

      //try init again
      campaign ! InitCampaign(startDate.toDate)
      expectMsg(EffectlessAck(Success("OK")))

      system.stop(campaign)
      Thread.sleep(1000)
    }
  }

  "NbaCampaign" must {
    "write/read through streamz" in {
      import scalaz.stream.Process
      import scalaz.stream.Process._
      import scala.concurrent.duration._

      val persistenceId = "results"
      val startDate = new DateTime().withZone(microservice.crawler.SCENTER_TIME_ZONE)
        .withDate(2013, 11, 29).withTime(23, 59, 59, 0)

      implicit val t = akka.util.Timeout(3 seconds)
      val events = NbaResult("atl", 108, "mia", 120, startDate.plusDays(1).toDate) ::
        NbaResult("cle", 78, "mia", 112, startDate.plusDays(2).toDate) ::
        NbaResult("den", 80, "okc", 89, startDate.plusDays(3).toDate) :: Nil

      val thFactory = new ThreadFactoryBuilder().setDaemon(true)
        .setNameFormat("thread-persistence-tasks").build()
      val ec = Executors.newFixedThreadPool(4, thFactory)

      val source: Process[Task, NbaResult] = for {
        p ← emitAll(events)
        i ← emit(p)
      } yield i

      val wrtOut: Sink[Task, NbaResult] = scalaz.stream.io.channel { obj =>
        Task.delay(println(s"${Thread.currentThread().getName} - write ${obj}"))
      }

      val writerTask = (source observe wrtOut to persistence.journaler(persistenceId)).run

      val readerTask = persistence.replay(persistenceId)
        .map(r => s"${Thread.currentThread().getName} - read ${r.data.toString}")
        .to(scalaz.stream.io.stdOutLines).take(events.size).run

      Task.fork(writerTask)(ec).runAsync(_ => ())
      Task.fork(readerTask)(ec).runAsync(_ => println(s"Done"))
      Thread.sleep(8000)
    }
  }
}
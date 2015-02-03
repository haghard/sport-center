package crawler

import akka.actor.{Props, ActorRef, Actor, ActorSystem}
import akka.cluster.routing.{ClusterRouterPoolSettings, ClusterRouterPool}
import akka.routing.RoundRobinPool
import akka.testkit.{TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import microservice.crawler.{CollectedResultBlock, CrawlerJob}
import org.joda.time.DateTime
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}

import scala.collection.JavaConversions._
import scala.concurrent.duration._

class CrawlerMasterSpec extends TestKit(ActorSystem("crawler", ConfigFactory.parseString("""
 |  akka {
 |    loglevel = "INFO"
 |    actor {
 |      provider = akka.cluster.ClusterActorRefProvider
 |    }
 |  }
""".stripMargin).withFallback(ConfigFactory.parseString("""
 | io-dispatcher {
 |   type = PinnedDispatcher
 |   executor = thread-pool-executor
 | }
""".stripMargin)).withFallback(ConfigFactory.load("app-setting"))))
  with WordSpecLike
  with MustMatchers
  with BeforeAndAfterAll {

  val teams = asScalaBuffer(system.settings.config
    .getConfig("app-settings")
    .getObjectList("teams")).map { ent ⇒
    val it = ent.entrySet().iterator()
    val item = it.next()
    item.getKey
  }

  trait ClusterRoundRobinPoolCreator extends WebRouterCreator {
    self: Actor ⇒

    private val routerProps = ClusterRouterPool(
      RoundRobinPool(nrOfInstances = 10),
      ClusterRouterPoolSettings(
        totalInstances = 10,
        maxInstancesPerNode = 5,
        allowLocalRoutees = true,
        useRole = Some(routerNodeRole))
    ).props(WebGetter.props(teams)).withDispatcher(dispatcher)

    lazy val createRouter: ActorRef =
      context.actorOf(routerProps, name = routerName)
  }

  override def afterAll = system.shutdown

  "CrawlerMaster with one node cluster" should {
    "eventually collect result with retry" in {
      val probe = TestProbe()
      val dt = new DateTime().withZone(microservice.crawler.SCENTER_TIME_ZONE)
        .withDate(2013, 11, 29)
        .withTime(23, 59, 59, 0)

      val urls = List(
        "http://www.nba.com/gameline/20131121/",
        "http://www.nba.com/gameline/20131122/",
        "http://www.nba.com/gameline/20131123/",
        "http://www.nba.com/gameline/20131124/",
        "http://www.nba.com/gameline/20131125/",
        "http://www.nba.com/gameline/20131126/",
        "http://www.nba.com/gameline/20131127/",
        "http://www.nba.com/gameline/20131128/",
        "http://www.nba.com/gameline/20131129/")

      val crawlerMaster = system.actorOf(Props(new CrawlerMaster(teams) with ClusterRoundRobinPoolCreator))
      crawlerMaster.tell(CrawlerJob(dt, urls), probe.ref)
      probe.expectMsgClass(30 second, classOf[CollectedResultBlock])
    }
  }
}
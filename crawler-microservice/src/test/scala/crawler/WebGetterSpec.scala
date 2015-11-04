package crawler

import akka.actor.ActorSystem
import akka.testkit.{ TestProbe, TestKit }
import com.typesafe.config.ConfigFactory
import domain.TeamAggregate.ResultAdded
import domain.serializer.ResultAddedEventSerializer
import microservice.crawler.NbaResult
import org.scalatest.{ BeforeAndAfterAll, MustMatchers, WordSpecLike }
import scala.collection.JavaConversions._
import scala.concurrent.duration._

object WebGetterSpec {

  val clusterConfig = ConfigFactory.parseString("""
| akka {
|    loglevel = "INFO"
|
|    crawler-dispatcher {
|      type = PinnedDispatcher
|      executor = "thread-pool-executor"
|    }
|  }
""".stripMargin).withFallback(ConfigFactory.load("app-setting.conf"))

}

//crawlerMicroservices/test:test-only *.WebGetterSpec
class WebGetterSpec extends TestKit(ActorSystem("Crawler", WebGetterSpec.clusterConfig))
    with WordSpecLike
    with MustMatchers
    with BeforeAndAfterAll {

  val waitTime = 15 seconds

  val teams = asScalaBuffer(system.settings.config
    .getConfig("app-settings")
    .getObjectList("teams")).map { ent ⇒
    val it = ent.entrySet().iterator()
    val item = it.next()
    item.getKey
  }

  override def afterAll = system.terminate()

  "WebGetter" should {
    "collect non empty results for specific page" in {
      val probe = TestProbe()
      val url =
        "http://www.nba.com/gameline/20121127/" //recap is absent
      //"http://www.nba.com/gameline/20151027/"
      //"http://www.nba.com/gameline/20140705/"
      //"http://www.nba.com/gameline/20140222/"
      //"http://www.nba.com/gameline/20121030/"
      //"http://www.nba.com/gameline/20141104/"
      //"http://www.nba.com/gameline/20121202/"

      val getter = system.actorOf(WebGetter.props(teams).withDispatcher("akka.crawler-dispatcher"), "web-getter-20151027")
      getter.tell(url, probe.ref)
      val (_, list) = probe.expectMsgClass(waitTime, classOf[(String, List[NbaResult])])
      println(list.mkString("\n"))
      system stop getter
    }
  }
  /*
  "WebGetter" should {
    "collect result from empty page" in {
      val probe = TestProbe()
      val url = "http://www.nba.com/gameline/20140628/"
      val getter = system.actorOf(WebGetter.props(teams).withDispatcher("akka.crawler-dispatcher"), "web-getter-20140628")
      getter.tell(url, probe.ref)
      probe.expectMsg(waitTime, (url, List()))
      system stop getter
    }
  }*/
}
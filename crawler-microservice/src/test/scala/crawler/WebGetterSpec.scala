package crawler

import akka.actor.ActorSystem
import akka.testkit.{ TestProbe, TestKit }
import com.typesafe.config.ConfigFactory
import microservice.crawler.NbaResult
import org.scalatest.{ BeforeAndAfterAll, MustMatchers, WordSpecLike }
import scala.collection.JavaConversions._
import scala.concurrent.duration._

object WebGetterSpec {

  def clusterConfig = ConfigFactory.parseString("""
| akka {
|    loglevel = "INFO"
|
|    io-dispatcher {
|      type = PinnedDispatcher
|      executor = "thread-pool-executor"
|    }
|  }
""".stripMargin).withFallback(ConfigFactory.load("app-setting.conf"))

}

class WebGetterSpec extends TestKit(ActorSystem("Crawler", WebGetterSpec.clusterConfig))
    with WordSpecLike
    with MustMatchers
    with BeforeAndAfterAll {

  val waitTime = 10 seconds

  val teams = asScalaBuffer(system.settings.config
    .getConfig("app-settings")
    .getObjectList("teams")).map { ent ⇒
    val it = ent.entrySet().iterator()
    val item = it.next()
    item.getKey
  }

  override def afterAll = system.shutdown

  "WebGetter" should {
    "collect non empty results for specific page" in {
      val probe = TestProbe()
      val url = "http://www.nba.com/gameline/20141104/"
      val getter = system.actorOf(WebGetter.props(teams))
      getter.tell(url, probe.ref)
      probe.expectMsgClass(waitTime, classOf[(String, List[NbaResult])])
      system stop getter
    }
  }

  "WebGetter" should {
    "collect result from empty page" in {
      val probe = TestProbe()
      val url = "http://www.nba.com/gameline/20140628/"
      val getter = system.actorOf(WebGetter.props(teams))
      getter.tell(url, probe.ref)
      probe.expectMsg(waitTime, (url, List()))
      system stop getter
    }
  }
}
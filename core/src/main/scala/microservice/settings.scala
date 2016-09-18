package microservice

import java.net.InetSocketAddress
import akka.actor._
import java.util.concurrent.TimeUnit
import com.github.nscala_time.time.Imports._
import com.typesafe.config.ConfigFactory
import org.joda.time.{ DateTime, DateTimeZone }

import scala.concurrent.duration.{ Duration, FiniteDuration }

object settings {

  trait SingletonPaths {

    def proxyName: String

    def singletonName: String

    def name: String

    def proxyPath = s"/user/$proxyName"

    def originalPath = s"/user/$name/$singletonName"
  }

  case class BrokerPorts(senderPort: Int, receiverPort: Int)
  case class CrawlerSettings(daysInBatch: Int, iterationPeriod: FiniteDuration, jobTimeout: FiniteDuration)
  case class Paths(crawlerPaths: ItemPaths, tweeterPaths: ItemPaths, updaterPaths: ItemPaths)
  case class ItemPaths(proxyName: String, singletonName: String, name: String) extends SingletonPaths
  case class TwitterAuth(apiKey: String, apiSecret: String, accessToken: String, accessTokenSecret: String)
  case class Cassandra(keyspace: String, table: String, address: InetSocketAddress)
  //case class SessionGfg(secret: String, sail: String, ttl: Long)
  case class Intervals(resultsPeriod: FiniteDuration, standingsPeriod: FiniteDuration)

  object CustomSettings extends ExtensionKey[CustomSettings]

  final class CustomSettings(system: ExtendedActorSystem) extends Extension {
    import scala.collection.JavaConversions.asScalaBuffer

    val refreshIntervals = {
      val cfg = system.settings.config.getConfig("app-settings")
      Intervals(FiniteDuration(cfg.getDuration("refresh.results", TimeUnit.SECONDS), TimeUnit.SECONDS),
        FiniteDuration(cfg.getDuration("refresh.standings", TimeUnit.SECONDS), TimeUnit.SECONDS))
    }

    val cassandra = {
      val dbCfg = ConfigFactory.load("internals").getConfig("db.cassandra")
      val casConf = system.settings.config.getConfig("cassandra-journal")
      Cassandra(casConf.getString("keyspace"), casConf.getString("table"), new InetSocketAddress(dbCfg.getString("seeds"), dbCfg.getInt("port")))
    }

    val teams = asScalaBuffer(system.settings.config
      .getConfig("app-settings")
      .getObjectList("teams")).map { ent ⇒
      val it = ent.entrySet().iterator()
      val item = it.next()
      item.getKey
    }

    val teamConferences = asScalaBuffer(system.settings.config
      .getConfig("app-settings")
      .getObjectList("teams"))
      .foldLeft(scala.collection.mutable.HashMap[String, String]()) { (acc, c) ⇒
        val it = c.entrySet().iterator()
        if (it.hasNext) {
          val entry = it.next()
          acc += (entry.getKey -> entry.getValue.render().replace("\"", ""))
        }
        acc
      }

    val stages = system.settings.config
      .getConfig("app-settings")
      .getObjectList("stages")
      .foldLeft(scala.collection.mutable.LinkedHashMap[String, String]()) { (acc, c) ⇒
        val it = c.entrySet().iterator()
        if (it.hasNext) {
          val entry = it.next()
          acc += (entry.getKey -> entry.getValue.render().replace("\"", ""))
        }
        acc
      }

    lazy val crawler = {
      val cfg = system.settings.config.getConfig("app-settings")
      CrawlerSettings(cfg.getInt("crawler.days-in-batch"),
        FiniteDuration(cfg.getDuration("crawler.iteration-period", TimeUnit.HOURS), TimeUnit.HOURS),
        FiniteDuration(cfg.getDuration("crawler.job-timeout", TimeUnit.SECONDS), TimeUnit.SECONDS))
    }

    val intervals = {
      var views0 = scala.collection.mutable.LinkedHashMap[Interval, String]()
      val timeZone = DateTimeZone.forOffsetHours(-5)
      var start: Option[DateTime] = None
      var end: Option[DateTime] = None
      var period: Option[String] = None

      for ((k, v) ← stages) {
        if (start.isEmpty) {
          start = Some(new DateTime(v).withZone(timeZone).withTime(23, 59, 59, 0))
          period = Some(k)
        } else {
          end = Some(new DateTime(v).withZone(timeZone).withTime(23, 59, 58, 0))
          val interval = (start.get to end.get)
          views0 = views0 += (interval -> period.get)
          start = Some(end.get.withTime(23, 59, 59, 0))
          period = Some(k)
        }
      }
      views0
    }

    lazy val twitterCreds = {
      val config = system.settings.config
      TwitterAuth(config.getString("API-Key"), config.getString("API-Secret"),
        config.getString("Access-Token"), config.getString("Access-Token-Secret"))
    }

    /*lazy val session = {
      SessionGfg(system.settings.config.getString("http.session"),
        system.settings.config.getString("http.salt"),
        system.settings.config.getLong("http.ttl"))
    }*/

    case class Timeouts(results: java.time.Duration, standings: java.time.Duration)
    lazy val timeouts = {
      Timeouts(
        system.settings.config.getDuration("timeouts.results"),
        system.settings.config.getDuration("timeouts.standings")
      )
    }
    //lazy val cloudToken = system.settings.config.getString("digital_ocean_api_token")
  }
}
package microservice

import akka.actor._
import com.github.nscala_time.time.Imports._
import org.joda.time.{ DateTime, DateTimeZone }

object settings {

  trait SingletonPaths {

    def proxyName: String

    def singletonName: String

    def name: String

    def proxyPath = s"/user/$proxyName"

    def originalPath = s"/user/$name/$singletonName"
  }

  case class ItemPaths(proxyName: String, singletonName: String, name: String) extends SingletonPaths

  case class Paths(crawlerPaths: ItemPaths, tweeterPaths: ItemPaths, updaterPaths: ItemPaths)

  case class BrokerPorts(senderPort: Int, receiverPort: Int)

  case class Twitter(apiKey: String, apiSecret: String, accessToken: String, accessTokenSecret: String)

  object CustomSettings extends ExtensionKey[CustomSettings]

  final class CustomSettings(system: ExtendedActorSystem) extends Extension {
    import scala.collection.JavaConversions.asScalaBuffer

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

    val journalUrl =
      system.settings.config.getString("casbah-journal.mongo-journal-url")

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
      Twitter(config.getString("API-Key"), config.getString("API-Secret"),
        config.getString("Access-Token"), config.getString("Access-Token-Secret"))
    }

    val twitterTeams = asScalaBuffer(system.settings.config
      .getConfig("app-settings").getObjectList("tweeter-teams"))
      .foldLeft(scala.collection.mutable.HashMap[String, String]()) { (acc, c) ⇒
        val it = c.entrySet().iterator()
        if (it.hasNext) {
          val entry = it.next()
          acc += (entry.getKey -> entry.getValue.render().replace("\"", ""))
        }
        acc
      }

    val broker = {
      val broker = system.settings.config.getConfig("broker")
      BrokerPorts(broker.getInt("sender-port"), broker.getInt("receiver-port"))
    }

    val clusterSingletons = {
      val paths = system.settings.config.getConfig("singletons")
      val crawlerPaths = paths.getConfig("crawler")
      val tweeterPaths = paths.getConfig("twitter")
      val updaterPaths = paths.getConfig("updater")

      Paths(
        ItemPaths(crawlerPaths.getString("proxyName"), crawlerPaths.getString("singletonName"), crawlerPaths.getString("name")),
        ItemPaths(tweeterPaths.getString("proxyName"), tweeterPaths.getString("singletonName"), tweeterPaths.getString("name")),
        ItemPaths(updaterPaths.getString("proxyName"), updaterPaths.getString("singletonName"), updaterPaths.getString("name"))
      )
    }

    lazy val cloudToken = system.settings.config.getString("digital_ocean_api_token")
  }
}
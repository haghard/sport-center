package microservice

import java.text.SimpleDateFormat
import java.util.{ TimeZone, Date }
import akka.actor.ExtendedActorSystem
import akka.serialization.Serializer
import org.joda.time.{ DateTimeZone, DateTime }
import spray.json.{ JsString, JsNumber, JsObject, JsValue }

import scalaz.Monoid

package object crawler {

  case object GetCrawlers
  case object GetBackends

  case class CrawlerJob(endDt: DateTime, urls: List[String])

  val SCENTER_TIME_ZONE = DateTimeZone.forID("EST")
  val TIME_ZONE = TimeZone.getTimeZone("EST")

  def searchFormatter() = {
    val local = new SimpleDateFormat("yyyy-MM-dd")
    local.setTimeZone(TIME_ZONE)
    local
  }

  def estFormatter() = {
    val local = new SimpleDateFormat("dd MMM yyyy hh:mm a z")
    local.setTimeZone(TIME_ZONE)
    local
  }

  object Location extends Enumeration {
    val Home = Value("home")
    val Away = Value("away")
    val All = Value("all")
  }

  object Conferences extends Enumeration {
    val West = Value("west")
    val East = Value("east")
  }

  case class JobFailed(reason: String)
  case class TimeOut(urls: List[String], results: List[NbaResult])
  case class StatsResult(meanWordLength: Double)
  case class SuccessCollected(list: List[NbaResult])
  case class NbaResult(homeTeam: String, homeScore: Int, awayTeam: String, awayScore: Int, dt: Date)

  case class CrawledNbaResult(opponent: String, homeScore: Int, awayScore: Int, dt: Date, lct: Location.Value)

  implicit object Sort extends Ordering[CrawledNbaResult] {
    override def compare(x: CrawledNbaResult, y: CrawledNbaResult): Int =
      x.dt.compareTo(y.dt)
  }

  implicit def M(teams: List[String]) = new Monoid[JsValue] {
    override val zero: JsValue =
      JsObject(teams.map(r ⇒ (r -> JsObject("w" -> JsNumber(0), "l" -> JsNumber(0)))): _*)

    override def append(f1: JsValue, f2: ⇒ JsValue): JsValue = {
      var standing: JsObject = null
      var result: JsObject = null

      if (f1.asJsObject.fields.get("dt").isDefined) {
        result = f1.asJsObject
        standing = f2.asJsObject
      } else {
        result = f2.asJsObject
        standing = f1.asJsObject
      }

      val h = result.fields("h").asInstanceOf[JsString].value
      val r = result.fields("r").asInstanceOf[JsString].value
      val hs = result.fields("hs").asInstanceOf[JsNumber]
      val rs = result.fields("rs").asInstanceOf[JsNumber]

      val hw = standing.fields(h).asJsObject.fields("w").asInstanceOf[JsNumber]
      val hl = standing.fields(h).asJsObject.fields("l").asInstanceOf[JsNumber]
      val rw = standing.fields(r).asJsObject.fields("w").asInstanceOf[JsNumber]
      val rl = standing.fields(r).asJsObject.fields("l").asInstanceOf[JsNumber]

      if (hs.value > rs.value) {
        JsObject(standing.fields + (h -> JsObject("w" -> JsNumber(hw.value + 1), "l" -> hl)) + (r -> JsObject("w" -> rw, "l" -> JsNumber(rl.value + 1))))
      } else {
        JsObject(standing.fields + (h -> JsObject("w" -> hw, "l" -> JsNumber(hl.value + 1))) + (r -> JsObject("w" -> JsNumber(rw.value + 1), "l" -> rl)))
      }
    }
  }
}

package microservice

import java.text.SimpleDateFormat
import java.util.{ TimeZone, Date }
import org.joda.time.{ DateTimeZone, DateTime }
import spray.json.{ JsString, JsNumber, JsObject, JsValue }
import scalaz.Monoid
import scalaz._, Scalaz._

package object crawler {

  case object GetCrawlers
  case object GetBackends

  case class CrawlerJob(endDt: DateTime, urls: List[String])

  val SCENTER_TIME_ZONE = DateTimeZone.forID("EST")
  val EST_TIME_ZONE = TimeZone.getTimeZone("EST")
  val JODA_EST = org.joda.time.DateTimeZone.forID("EST")

  def searchFormatter() = {
    val local = new SimpleDateFormat("yyyy-MM-dd")
    local.setTimeZone(EST_TIME_ZONE)
    local
  }

  def estFormatter() = {
    val local = new SimpleDateFormat("dd MMM yyyy hh:mm a z")
    local.setTimeZone(EST_TIME_ZONE)
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

  case class NbaResult(homeTeam: String, homeScore: Int, awayTeam: String, awayScore: Int, dt: Date,
    homeScoreBox: String = "", awayScoreBox: String = "",
    homeTotal: Total = Total(), awayTotal: Total = Total(),
    homeBox: List[PlayerLine] = Nil, awayBox: List[PlayerLine] = Nil)

  case class Total(min: Int = 0, fgmA: String = "", threePmA: String = "", ftmA: String = "", minusSlashPlus: String = "",
    offReb: Int = 0, defReb: Int = 0, totalReb: Int = 0, ast: Int = 0, pf: Int = 0,
    steels: Int = 0, to: Int = 0, bs: Int = 0, ba: Int = 0, pts: Int = 0)

  case class PlayerLine(name: String = "", pos: String = "", min: String = "", fgmA: String = "",
    threePmA: String = "", ftmA: String = "", minusSlashPlus: String = "",
    offReb: Int = 0, defReb: Int = 0, totalReb: Int = 0, ast: Int = 0, pf: Int = 0, steels: Int = 0,
    to: Int = 0, bs: Int = 0, ba: Int = 0, pts: Int = 0)

  case class CrawledNbaResult(opponent: String, homeScore: Int, awayScore: Int, dt: Date, lct: Location.Value)

  case class NbaResultView(homeTeam: String, homeScore: Int,
    awayTeam: String, awayScore: Int, dt: Date,
    homeScoreBox: String, awayScoreBox: String)

  class HOMap[K[_] <: AnyRef, V[_] <: AnyRef](delegate: Map[AnyRef, AnyRef]) {
    def apply[A](key: K[A]): V[A] = delegate(key).asInstanceOf[V[A]]
    def get[A](key: K[A]): Option[V[A]] = delegate.get(key).asInstanceOf[Option[V[A]]]
    def +[A](pair: (K[A], V[A])): HOMap[K, V] = new HOMap[K, V](delegate + pair.asInstanceOf[(AnyRef, AnyRef)])
    def contains[A](key: K[A]): Boolean = delegate contains key
  }

  object HOMap {
    def apply[K[_] <: AnyRef, V[_] <: AnyRef](pairs: ((K[A], V[A]) forSome { type A })*): HOMap[K, V] =
      new HOMap[K, V](Map(pairs.map { _.asInstanceOf[(AnyRef, AnyRef)] }: _*))
  }

  def Lens[State] = HOMap[Option, ({ type λ[α] = (State) => α => State })#λ]()

  implicit object Ord extends scala.Ordering[NbaResultView] {
    override def compare(x: NbaResultView, y: NbaResultView): Int =
      x.dt.compareTo(y.dt)
  }

  implicit object Sort extends scala.Ordering[CrawledNbaResult] {
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

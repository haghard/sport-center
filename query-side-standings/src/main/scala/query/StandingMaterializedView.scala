package query

import java.util.Date
import java.math.MathContext
import microservice.crawler.NbaResult
import com.github.nscala_time.time.Imports._
import microservice.settings.CustomSettings
import query.StandingViewRouter.QueryStandingByDate
import microservice.http.RestService.ResponseBody
import akka.actor._

import scala.collection.mutable

object StandingMaterializedView {

  private val mc = new MathContext(2)
  private val season = "season-(.*)".r
  private val playoff = "playoff-(.*)".r
  private val summer = "summer-(.*)".r

  case class StandingLine(team: String, data: SeasonMetrics)

  case class SeasonMetrics(w: Int = 0, l: Int = 0, pct: BigDecimal = 0, homeW: Int = 0, homeL: Int = 0, roadW: Int = 0, roadL: Int = 0)

  case class SeasonStandingResponse(west: Seq[StandingLine] = Seq.empty, east: Seq[StandingLine] = Seq.empty, count: Int = 0) extends ResponseBody

  case class PlayOffStandingResponse(stages: mutable.Map[String, List[NbaResult]], count: Int = 0) extends ResponseBody

  private[StandingMaterializedView] trait ViewBuilder {
    def add: NbaResult ⇒ Unit
    def query(replyTo: ActorRef, vName: Option[String]): Unit
  }

  private[StandingMaterializedView] final class PlayoffViewBuilder extends ViewBuilder {
    private val stageHashes = mutable.HashMap[Set[String], Date]()
    private val playOffResults = mutable.HashMap[Set[String], List[NbaResult]]()
    private val first = List.range(1, 9).map(_ + ". first round")
    private val second = List.range(1, 5).map(_ + ". second round")
    private val semifinal = List.range(1, 3).map(_ + ". conf final")
    private val stageNames = first ::: second ::: semifinal ::: List("final")

    private def hash(homeTeam: String, roadTeam: String): Set[String] = Set(homeTeam, roadTeam)

    override def add =
      r ⇒ {
        val set = hash(r.homeTeam, r.roadTeam)
        stageHashes += (set -> r.dt)
        val rs = playOffResults.getOrElse(set, List[NbaResult]())
        val updated = rs :+ r
        playOffResults += (set -> updated)
      }

    override def query(replyTo: ActorRef, vName: Option[String]) = {
      vName.fold(replyTo ! "Can't query based on empty view") { name ⇒
        val temp = stageHashes.toSeq.sortWith { (l, r) ⇒
          l._2.compareTo(r._2) match {
            case -1 ⇒ true
            case _  ⇒ false
          }
        }
        val local = temp.foldLeft((mutable.Map[String, List[NbaResult]](), stageNames.toBuffer)) { (map, c) ⇒
          map._1 += (map._2.head -> playOffResults(c._1))
          map._1 -> map._2.tail
        }
        replyTo ! PlayOffStandingResponse(local._1)
      }
    }
  }

  private[StandingMaterializedView] final class SeasonViewBuilder(settings: CustomSettings) extends ViewBuilder {

    private var storage = {
      val local = mutable.HashMap[String, SeasonMetrics]()
      settings.teams.foreach { t ⇒
        local += (t -> SeasonMetrics())
      }
      local
    }

    override def add =
      r ⇒ for {
        hm ← storage.get(r.homeTeam)
        rm ← storage.get(r.roadTeam)
      } yield {
        if (r.homeScore > r.roadScore) {
          storage += (r.homeTeam -> hm.copy(w = hm.w + 1, pct = BigDecimal.decimal(((hm.w + 1).toFloat / ((hm.w + 1) + hm.l)), mc), homeW = hm.homeW + 1))
          storage += (r.roadTeam -> rm.copy(l = rm.l + 1, pct = BigDecimal.decimal((rm.w.toFloat / (rm.w + rm.l + 1)), mc), roadL = rm.roadL + 1))
        } else {
          storage += (r.homeTeam -> hm.copy(l = hm.l + 1, pct = BigDecimal.decimal(((hm.w + 1).toFloat / ((hm.w + 1) + hm.l)), mc), homeL = hm.homeL + 1))
          storage += (r.roadTeam -> rm.copy(w = rm.w + 1, pct = BigDecimal.decimal((rm.w.toFloat / (rm.w + rm.l + 1)), mc), roadW = rm.roadW + 1))
        }
      }

    override def query(replyTo: ActorRef, vName: Option[String]) = {
      vName.fold(replyTo ! "Can't query based on empty view") { name ⇒
        val table = (storage.toSeq.sortWith(_._2.w > _._2.w) partition { item ⇒
          settings.teamConferences(item._1) match {
            case "west" ⇒ true
            case "east" ⇒ false
          }
        })
        replyTo ! SeasonStandingResponse(table._1.map(x ⇒ StandingLine(x._1, x._2)), table._2.map(x ⇒ StandingLine(x._1, x._2)))
      }
    }
  }

  def props(settings: CustomSettings) = Props(new StandingMaterializedView(settings))
}

class StandingMaterializedView private (settings: CustomSettings) extends Actor with ActorLogging {
  import query.StandingMaterializedView._

  private var viewName: Option[String] = None
  private var view: Option[ViewBuilder] = None

  override def preStart() = log.info("preStart: {}", self)

  override def postStop = log.info("postStop: {}", self)

  override def receive = initial

  private val initial: Receive = {
    case r: NbaResult ⇒ {
      viewName = (for {
        (k, v) ← settings.intervals
        if k.contains(new DateTime(r.dt))
      } yield {
        v
      }).headOption
      viewName foreach (x ⇒ x match {
        case season(y)  ⇒ view = Some(new SeasonViewBuilder(settings))
        case playoff(y) ⇒ view = Some(new PlayoffViewBuilder)
        case summer(y)  ⇒ view = Some(new SeasonViewBuilder(settings))
      })
      context become activate(r)
    }
    case QueryStandingByDate(dt) ⇒ sender() ! "View does not ready yet. Please try later"
  }

  private def activate(r: NbaResult) = {
    view.foreach(_.add(r))
    active
  }

  private def active: Actor.Receive = {
    case r: NbaResult ⇒ view.foreach(_.add(r))
    case QueryStandingByDate(dt) ⇒
      val replyTo = sender()
      view.foreach(_.query(replyTo, viewName))
  }
}
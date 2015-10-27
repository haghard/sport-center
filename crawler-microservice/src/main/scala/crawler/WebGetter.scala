package crawler

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import scala.collection.JavaConversions._
import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import microservice.crawler.{ PlayerLine, Total, NbaResult }

object WebGetter {
  val TIMEOUT = 7000
  val NAME = """<div(.)+>\s*(\w+)\s*</div>""".r
  val SCORE = """<div(.)+>\s*(\d+)\s*</div>""".r
  val Q_SCORE = """<td class="score">(\d{1,3})</td>""".r
  val TP = """<div class="nbaFnlStatTxSm">\s*(\d{1,2}:\d{1,2})\s*(pm|am)\s*et\s*</div>""".r
  val Link = """<a(.+)href="([^"]*)"(.*)""".r
  val DATE_EXTRACTOR = """http://www.nba.com/gameline/(\d{4})(\d{2})(\d{2})/""".r
  val Header = """<td class="nbaGITeamHdrStats">(.*)</td>""".r
  val Field = """<td(.*)>(.*)</td>""".r
  val PlayerName = """<(.*)><a href="(.*)">(.*)</a></td>""".r

  case class CrawlerException(sender: ActorRef, cause: Throwable, url: String) extends Exception(cause)

  private[WebGetter] val webDispatcher = "akka.io-dispatcher"

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

  private[WebGetter] def Lens[State] = HOMap[Option, ({ type λ[α] = (State) => α => State })#λ]()

  private[WebGetter] val totalLens = Lens[Total]
    .+(Option("min") -> { (in: Total) =>
      (chunkWithMin: String) =>
        chunkWithMin match {
          case Field(_, min0) => in.copy(min = min0.toInt)
          case other          => in
        }
    })
    .+(Option("fgmA") -> { (in: Total) =>
      (chunkWithfgmA: String) =>
        chunkWithfgmA match {
          case Field(_, fgmA0) => in.copy(fgmA = fgmA0)
          case other           => in
        }
    })
    .+(Option("threePmA") -> { (in: Total) =>
      (chunkWithThreePmA: String) =>
        chunkWithThreePmA match {
          case Field(_, threePmA0) => in.copy(threePmA = threePmA0)
          case other               => in
        }
    })
    .+(Option("ftmA") -> { (in: Total) =>
      (chunkWithftmA: String) =>
        chunkWithftmA match {
          case Field(_, ftmA0) => in.copy(ftmA = ftmA0)
          case other           => in
        }
    })
    .+(Option("minusSlashPlus") -> { (in: Total) =>
      (chunkWithMinusSlashPlus: String) =>
        chunkWithMinusSlashPlus match {
          case Field(_, minusSlashPlus0) if (minusSlashPlus0 != "&nbsp;") => in.copy(minusSlashPlus = minusSlashPlus0)
          case other => in
        }
    })
    .+(Option("offReb") -> { (in: Total) =>
      (chunkWithOffReb: String) =>
        chunkWithOffReb match {
          case Field(_, offReb0) if (offReb0.trim != "") =>
            in.copy(offReb = offReb0.trim.toInt)
          case other => in
        }
    })
    .+(Option("defReb") -> { (in: Total) =>
      (chunkWithDefReb: String) =>
        chunkWithDefReb match {
          case Field(_, defReb0) if (defReb0.trim != "") =>
            in.copy(defReb = defReb0.trim.toInt)
          case other => in
        }
    })
    .+(Option("totalReb") -> { (in: Total) =>
      (chunkWithTotalReb: String) =>
        chunkWithTotalReb match {
          case Field(_, totalReb0) if (totalReb0.trim != "") =>
            in.copy(totalReb = totalReb0.trim.toInt)
          case other => in
        }
    })
    .+(Option("ast") -> { (in: Total) =>
      (chunckWithAst: String) =>
        chunckWithAst match {
          case Field(_, ast0) if (ast0.trim != "") =>
            in.copy(ast = ast0.trim.toInt)
          case other => in
        }
    })
    .+(Option("pf") -> { (in: Total) =>
      (chunkWithpf: String) =>
        chunkWithpf match {
          case Field(_, pf0) if (pf0.trim != "") =>
            in.copy(pf = pf0.trim.toInt)
          case other => in
        }
    })
    .+(Option("steels") -> { (in: Total) =>
      (chunkWithSteels: String) =>
        chunkWithSteels match {
          case Field(_, steels0) if (steels0.trim != "") =>
            in.copy(steels = steels0.trim.toInt)
          case other => in
        }
    })
    .+(Option("to") -> { (in: Total) =>
      (chunkWithto: String) =>
        chunkWithto match {
          case Field(_, to0) if (to0.trim != "") =>
            in.copy(to = to0.trim.toInt)
          case other => in
        }
    })
    .+(Option("bs") -> { (in: Total) =>
      (chunkWithbs: String) =>
        chunkWithbs match {
          case Field(_, bs0) if (bs0.trim != "") =>
            in.copy(bs = bs0.trim.toInt)
          case other => in
        }
    })
    .+(Option("ba") -> { (in: Total) =>
      (chunkWithba: String) =>
        chunkWithba match {
          case Field(_, ba0) if (ba0.trim != "") =>
            in.copy(ba = ba0.trim.toInt)
          case other => in
        }
    })
    .+(Option("pts") -> { (in: Total) =>
      (chunkWithpts: String) =>
        chunkWithpts match {
          case Field(_, pts0) if (pts0.trim != "") =>
            in.copy(pts = pts0.trim.toInt)
          case other => in
        }
    })

  /**
   *
   *
   */
  private[WebGetter] val playerLens = Lens[PlayerLine]
    .+(Option("name") -> { (in: PlayerLine) =>
      (chunkWithName: String) =>
        chunkWithName match {
          case PlayerName(_, _, name0) => in.copy(name = name0)
          case other                   => in
        }
    })
    .+(Option("pos") -> { (in: PlayerLine) =>
      (chunkWithPos: String) =>
        chunkWithPos match {
          case Field(_, v) if (v != "&nbsp;") => in.copy(pos = v)
          case other                          => in
        }
    })
    .+(Option("min") -> { (in: PlayerLine) =>
      (chunkWithMin: String) =>
        chunkWithMin match {
          case Field(_, min0) => in.copy(min = min0)
          case other          => in
        }
    })
    .+(Option("fgmA") -> { (in: PlayerLine) =>
      (chunkWithfgmA: String) =>
        chunkWithfgmA match {
          case Field(_, fgmA0) => in.copy(fgmA = fgmA0)
          case other           => in
        }
    })
    .+(Option("threePmA") -> { (in: PlayerLine) =>
      (chunkWithThreePma: String) =>
        chunkWithThreePma match {
          case Field(_, threePma0) => in.copy(threePmA = threePma0)
          case other               => in
        }
    })
    .+(Option("ftmA") -> { (in: PlayerLine) =>
      (chunkWithFtmA: String) =>
        chunkWithFtmA match {
          case Field(_, ftmA0) => in.copy(ftmA = ftmA0)
          case other           => in
        }
    })
    .+(Option("minusSlashPlus") -> { (in: PlayerLine) =>
      (chunkWithMinusSlashPlus: String) =>
        chunkWithMinusSlashPlus match {
          case Field(_, minusSlashPlus0) => in.copy(minusSlashPlus = minusSlashPlus0)
          case other                     => in
        }
    })
    .+(Option("offReb") -> { (in: PlayerLine) =>
      (chunkWithOffReb: String) =>
        chunkWithOffReb match {
          case Field(_, offReb0) if (offReb0.trim != "") =>
            in.copy(offReb = offReb0.trim.toInt)
          case other => in
        }
    })
    .+(Option("defReb") -> { (in: PlayerLine) =>
      (chunkWithDefReb: String) =>
        chunkWithDefReb match {
          case Field(_, defReb0) if (defReb0.trim != "") =>
            in.copy(defReb = defReb0.trim.toInt)
          case other => in
        }
    })
    .+(Option("totalReb") -> { (in: PlayerLine) =>
      (chunkWithTotalReb: String) =>
        chunkWithTotalReb match {
          case Field(_, totalReb0) if (totalReb0.trim != "") =>
            in.copy(totalReb = totalReb0.trim.toInt)
          case other => in
        }
    })
    .+(Option("ast") -> { (in: PlayerLine) =>
      (chunckWithAst: String) =>
        chunckWithAst match {
          case Field(_, ast0) if (ast0.trim != "") =>
            in.copy(ast = ast0.trim.toInt)
          case other => in
        }
    })
    .+(Option("pf") -> { (in: PlayerLine) =>
      (chunkWithpf: String) =>
        chunkWithpf match {
          case Field(_, pf0) if (pf0.trim != "") =>
            in.copy(pf = pf0.trim.toInt)
          case other => in
        }
    })
    .+(Option("steels") -> { (in: PlayerLine) =>
      (chunkWithSteels: String) =>
        chunkWithSteels match {
          case Field(_, steels0) if (steels0.trim != "") =>
            in.copy(steels = steels0.trim.toInt)
          case other => in
        }
    })
    .+(Option("to") -> { (in: PlayerLine) =>
      (chunkWithto: String) =>
        chunkWithto match {
          case Field(_, to0) if (to0.trim != "") =>
            in.copy(to = to0.trim.toInt)
          case other => in
        }
    })
    .+(Option("bs") -> { (in: PlayerLine) =>
      (chunkWithbs: String) =>
        chunkWithbs match {
          case Field(_, bs0) if (bs0.trim != "") =>
            in.copy(bs = bs0.trim.toInt)
          case other => in
        }
    })
    .+(Option("ba") -> { (in: PlayerLine) =>
      (chunkWithba: String) =>
        chunkWithba match {
          case Field(_, ba0) if (ba0.trim != "") =>
            in.copy(ba = ba0.trim.toInt)
          case other => in
        }
    })
    .+(Option("pts") -> { (in: PlayerLine) =>
      (chunkWithpts: String) =>
        chunkWithpts match {
          case Field(_, pts0) if (pts0.trim != "") =>
            in.copy(pts = pts0.trim.toInt)
          case other => in
        }
    })

  import scalaz._, Scalaz._
  private[WebGetter] val playerKeys = List("name".some, "pos".some, "min".some, "fgmA".some, "threePmA".some, "ftmA".some,
    "minusSlashPlus".some, "offReb".some, "defReb".some, "totalReb".some, "ast".some, "pf".some,
    "steels".some, "to".some, "bs".some, "ba".some, "pts".some)

  private[WebGetter] val totalKeys = List("min".some, "fgmA".some, "threePmA".some, "ftmA".some,
    "minusSlashPlus".some, "offReb".some, "defReb".some, "totalReb".some, "ast".some, "pf".some,
    "steels".some, "to".some, "bs".some, "ba".some, "pts".some)

  def props(teams: Seq[String]) = Props(new WebGetter(teams))
    .withDispatcher(webDispatcher)
}

class WebGetter(teams: Seq[String]) extends Actor with ActorLogging {
  import crawler.WebGetter._

  override def postRestart(reason: Throwable) =
    log.info("WebGetter {} has been restarted ", self.path)

  override def receive: Receive = {
    case url: String ⇒ parse(url, sender())
  }

  private def parseTotal(total: Element): Total = {
    val chunks = total.getElementsByTag("td").toVector.drop(2)
    (chunks zip totalKeys)./:(Total()) { (state, chunkKey) =>
      totalLens(chunkKey._2)(state)(chunkKey._1.toString)
    }
  }

  private def parsePlayersBox(players: List[Element]): List[PlayerLine] = {
    players.tail.:\(List.empty[PlayerLine]) { (c, acc) =>
      val chunks = c.getElementsByTag("td").toList
      (chunks zip playerKeys)./:(PlayerLine()) { (state, chunkKey) =>
        playerLens(chunkKey._2)(state)(chunkKey._1.toString)
      } :: acc
    }
  }

  private def parse(url: String, sender: ActorRef) = {
    import com.github.nscala_time.time.Imports._
    val document = try {
      Jsoup.connect(url).ignoreHttpErrors(true).timeout(TIMEOUT).get
    } catch {
      case e: Throwable ⇒ throw new CrawlerException(sender, e, url)
    }

    val games = for {
      root ← Option(document.getElementById("nbaSSOuter"))
    } yield {
      root.getElementsByClass("Final").toList :::
        root.getElementsByClass("Recap").toList ::: root.getElementsByClass("RecapOT").toList
    }

    val gamesElements = games.getOrElse(List[Element]())
    val accum = gamesElements.foldLeft(List[NbaResult]()) { (acc, c) ⇒
      val timeElement = c.getElementsByClass("nbaFnlStatTxSm").toList.headOption
      val gameTime = timeElement.map(_.toString).collect({ case TP(t, ampm) ⇒ t + ":" + ampm })
      val time = gameTime.map(_.split(":"))
        .flatMap(array ⇒ if (array.length == 3) Some(array) else None)
        .flatMap { ar ⇒
          val d = Option(url).collect { case DATE_EXTRACTOR(y, m, d) ⇒ (y.toInt, m.toInt, d.toInt) }.head
          val dt0 = new DateTime().withZone(DateTimeZone.forOffsetHours(-5)).withDate(d._1, d._2, d._3)
          val gameDt = if (ar(2) == "am") {
            if (ar(0).toInt == 12) dt0.withTime(0, ar(1).toInt, 0, 0)
            else dt0.withTime(ar(0).toInt, ar(1).toInt, 0, 0)
          } else {
            if (ar(0).toInt == 12) dt0.withTime(12, ar(1).toInt, 0, 0)
            else dt0.withTime(ar(0).toInt + 12, ar(1).toInt, 0, 0)
          }
          Option(gameDt)
        }

      val awayTeamNameAndScore = c.getElementsByClass("nbaModTopTeamAw").toList.headOption.flatMap { el ⇒
        val name = el.getElementsByClass("nbaModTopTeamName").toList.headOption.map(_.toString)
          .collect { case NAME(_, name) ⇒ name }.filter(teams.contains(_))
        val score = el.getElementsByClass("nbaModTopTeamNum").toList.headOption.map(_.toString).collect { case SCORE(_, n) ⇒ n.toInt }
        name.flatMap(n ⇒ score.map(s ⇒ (n, s)))
      }

      val homeTeamNameAndScore = c.getElementsByClass("nbaModTopTeamHm").toList.headOption.flatMap { el ⇒
        val name = el.getElementsByClass("nbaModTopTeamName").toList.headOption.map(_.toString)
          .collect { case NAME(_, name) ⇒ name }.filter(teams.contains(_))
        val score = el.getElementsByClass("nbaModTopTeamNum").toList.headOption.map(_.toString) collect { case SCORE(_, n) ⇒ n.toInt }
        name.flatMap(n ⇒ score.map(s ⇒ (n, s)))
      }

      val sbs: Option[(String, String)] = c.getElementsByClass("nbaMnQuScores").toList.headOption.flatMap { e =>
        val trs = e.getElementsByTag("tr").toVector

        val scores: Option[(Element, Element)] = if (trs.size == 3) {
          Option((trs(0), trs(2)))
        } else if (trs.size >= 4) {
          Option((trs(1), trs(3)))
        } else None

        scores.map { els =>
          val away = els._1.getElementsByClass("score").toList.map(_.toString match {
            case Field(_, n) => n.toInt
            case other       => 0
          }).mkString("-")
          val home = els._2.getElementsByClass("score").toList.map(_.toString match {
            case Field(_, n) => n.toInt
            case other       => 0
          }).mkString("-")
          (home, away)
        }
      }

      val totals = c.getElementsByClass("nbaFnlMnRecapDiv").get(0)
        .select("a[href]").get(0).toString
        .split("\n")(0).trim match {
          case Link(_, recap, _) =>
            val boxScore = try {
              val boxUri = s"http://www.nba.com$recap"
              Jsoup.connect(boxUri).ignoreHttpErrors(true).timeout(TIMEOUT).get
            } catch {
              case e: Throwable ⇒ throw new CrawlerException(sender, e, url)
            }
            val boxes = boxScore.getElementById("nbaGIboxscore").select("table[id]")
            if (boxes.size == 2) {
              val awayTr = boxes.get(0).getElementsByTag("tr").toList
              val awayRows = awayTr.tail.tail
              val (awayPlayers, totalAway) = awayRows.splitAt(awayRows.size - 2)
              val away = parsePlayersBox(awayPlayers)
              val awayTotal0 = parseTotal(totalAway.head)

              val homeTr = boxes.get(1).getElementsByTag("tr").toList
              val homeRows = homeTr.tail.tail
              val (homePlayers, homeTotal) = homeRows.splitAt(homeRows.size - 2)
              val home = parsePlayersBox(homePlayers)
              val homeTotal0 = parseTotal(homeTotal.head)
              Option((homeTotal0, awayTotal0, home, away))
            } else {
              log.info(s"Expected 2 ScoreBoxes by actual ${boxes.size}")
              None
            }
          case other =>
            log.info("Error while extracting ScoreBoxes link:" + other.toString)
            None
        }

      (for {
        t ← time
        a ← awayTeamNameAndScore
        h ← homeTeamNameAndScore
        (hsb, asb) <- sbs
        (ht, at, hBox, aBox) <- totals
      } yield { acc :+ NbaResult(h._1, h._2, a._1, a._2, t.toDate, hsb, asb, ht, at, hBox, aBox) })
        .getOrElse(acc)
    }

    log.info("WebGetter uri:{} has been completed with size: {}", url, accum.size)
    sender ! (url, accum)
  }
}
//log.info("away: {}", away.mkString(";"))
//log.info("awayTotal: {}", awayTotal0.toString)
//log.info("home: {}", home.mkString(";"))
//log.info("homeTotal: {}", homeTotal0.toString)
package crawler.writer

import akka.event.LoggingAdapter
import crawler.RegexSupport
import crawler.writer.Crawler.CrawlPeriod
import microservice.crawler.{ PlayerLine, Total, NbaResult }
import org.joda.time.DateTime
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import scala.annotation.implicitNotFound
import scala.collection.JavaConversions._
import scala.util.Try
import scalaz._, Scalaz._

object Crawler {

  trait CrawlPeriod {
    def description: String
  }

  case class From2012(description: String) extends CrawlPeriod
  case class From2015(description: String) extends CrawlPeriod

  implicit def from2012 = new Crawler[From2012] {
    val NAME = """<div(.)+>\s*(\w+)\s*</div>""".r
    val SCORE = """<div(.)+>\s*(\d+)\s*</div>""".r
    val Q_SCORE = """<td class="score">(\d{1,3})</td>""".r
    val TP = """<div class="nbaFnlStatTxSm">\s*(\d{1,2}:\d{1,2})\s*(pm|am)\s*et\s*</div>""".r
    val Link = """<a(.+)href="([^"]*)"(.*)""".r
    val DATE_EXTRACTOR = """http://www.nba.com/gameline/(\d{4})(\d{2})(\d{2})/""".r
    val Header = """<td class="nbaGITeamHdrStats">(.*)</td>""".r

    private def parseTotal(total: Element): Total = {
      val chunks = total.getElementsByTag("td").toVector.drop(2)
      (chunks zip totalKeys)./:(Total()) { (state, chunkKey) =>
        totalLens(chunkKey._2)(state)(chunkKey._1.toString)
      }
    }

    private def parsePlayersBox(players: List[Element]): List[PlayerLine] = {
      players.tail.:\(List[PlayerLine]()) { (c, acc) =>
        val chunks = c.getElementsByTag("td").toList
        (chunks zip playerKeys)./:(PlayerLine()) { (state, chunkKey) =>
          playerLens(chunkKey._2)(state)(chunkKey._1.toString)
        } :: acc
      }
    }

    override def crawl(url: String, teams: Seq[String], log: LoggingAdapter): List[NbaResult] = {
      val page = fetchWebPage(url)
      val games = for {
        element ← Option(page.getElementById("nbaSSOuter"))
      } yield {
        element.getElementsByClass("Final").toList :::
          element.getElementsByClass("Recap").toList ::: element.getElementsByClass("RecapOT").toList
      }

      val gamesElements = games.getOrElse(List[Element]())
      val acc = gamesElements.foldLeft(List[NbaResult]()) { (acc, c) ⇒
        val timeElement = c.getElementsByClass("nbaFnlStatTxSm").toList.headOption
        val gameTime = timeElement.map(_.toString).collect({ case TP(t, ampm) ⇒ t + ":" + ampm })
        val time = gameTime.map(_.split(":"))
          .flatMap(array ⇒ if (array.length == 3) Some(array) else None)
          .flatMap { ar ⇒
            val d = Option(url).collect { case DATE_EXTRACTOR(y, m, d) ⇒ (y.toInt, m.toInt, d.toInt) }.head
            val dt0 = new DateTime().withZone(microservice.crawler.JODA_EST).withDate(d._1, d._2, d._3)
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
              case field(_, n) => n.toInt
              case other       => 0
            }).mkString("-")
            val home = els._2.getElementsByClass("score").toList.map(_.toString match {
              case field(_, n) => n.toInt
              case other       => 0
            }).mkString("-")
            (home, away)
          }
        }

        val totals = Try {
          c.getElementsByClass("nbaFnlMnRecapDiv").get(0)
            .select("a[href]").get(0).toString
            .split("\n")(0).trim match {
              case Link(_, recap, _) =>
                val boxUri = s"http://www.nba.com$recap"
                val boxScore = fetchWebPage(boxUri)

                val boxes = boxScore.getElementById("nbaGIboxscore").select("table[id]")
                if (boxes.size == 2) {
                  val awayTr = boxes.get(0).getElementsByTag("tr").toList
                  val awayRows = awayTr.tail.tail
                  val (awayPlayers, totalAway) = awayRows.splitAt(awayRows.size - 2)
                  val awayBox = parsePlayersBox(awayPlayers)
                  val awayTotal0 = parseTotal(totalAway.head)

                  val homeTr = boxes.get(1).getElementsByTag("tr").toList
                  val homeRows = homeTr.tail.tail
                  val (homePlayers, homeTotal) = homeRows.splitAt(homeRows.size - 2)
                  val homeBox = parsePlayersBox(homePlayers)
                  val homeTotal0 = parseTotal(homeTotal.head)
                  Option((homeTotal0, awayTotal0, homeBox, awayBox))
                } else {
                  log.info(s"Expected 2 ScoreBoxes by actual ${boxes.size}")
                  None
                }
              case other =>
                log.info("Error while extracting ScoreBoxes link:" + other.toString)
                None
            }
        }.recover {
          case e: Throwable =>
            log.info("Error while finding recap for {} cause: {}", url, e.getMessage)
            Option((Total(), Total(), Nil, Nil))
        }.get

        (for {
          t ← time
          a ← awayTeamNameAndScore
          h ← homeTeamNameAndScore
          (hsb, asb) <- sbs
          (ht, at, hBox, aBox) <- totals
        } yield { acc :+ NbaResult(h._1, h._2, a._1, a._2, t.toDate, hsb, asb, ht, at, hBox, aBox) })
          .getOrElse(acc)
      }
      acc
    }
  }

  /**
   *
   *
   */
  implicit def from2015 = new Crawler[From2015] {
    val extTags = "[<p>|</p>]"
    val Details = """<a\sclass="recapAnc"\shref="([^"]*)">""".r
    val Score = """<td class="score">(.+)</td>""".r
    val Time = """<p>Final(.+)<span>(.+)(\s)+et</span></p>""".r
    val TimeParts = """(.+):(.+)\s(pm|am)""".r
    val Date = """http://www.nba.com/gameline/(\d{4})(\d{2})(\d{2})/""".r

    private def extractDetailUrls(els: List[Element]) = {
      (els.foldLeft(List[Valid[String]]()) { (acc, el) =>
        val lines = (el.toString).split("\n")
        (if (lines.length > 0) {
          lines(0) match {
            case Details(uri) => uri.successNel
            case other        => s"Parse error: Regexp for details didn't work out for $el".failureNel
          }
        } else s"Parse error: Lines length == 0 for $el".failureNel) :: acc
      }).sequenceU
    }

    private def extractDateTime(node: org.jsoup.nodes.Node, d: (Int, Int, Int)): ValidDate = {
      node.childNodes().get(0).toString match {
        case Time(_, time, _) =>
          val timeParts = time.trim match { case TimeParts(h, m, marker) => Array(h, m, marker) }
          val dt = new DateTime().withZone(microservice.crawler.JODA_EST).withDate(d._1, d._2, d._3)
          (if (timeParts(2) == "am") {
            if (timeParts(0).toInt == 12) dt.withTime(0, timeParts(1).toInt, 0, 0)
            else dt.withTime(timeParts(0).toInt, timeParts(1).toInt, 0, 0)
          } else {
            if (timeParts(0).toInt == 12) dt.withTime(12, timeParts(1).toInt, 0, 0)
            else dt.withTime(timeParts(0).toInt + 12, timeParts(1).toInt, 0, 0)
          }).successNel
        case other => s"Parse error: Regexp for time didn't work out for $other".failureNel[DateTime]
      }
    }

    private def extractTeams(node: org.jsoup.nodes.Node): Valid[String] = {
      val teams = node.childNodes()
      if (teams.size() == 2) {
        val awayTeam = teams.get(0).childNode(0).toString.replaceAll(extTags, "")
        val homeTeam = teams.get(1).childNode(0).toString.replaceAll(extTags, "")
        s"$awayTeam/$homeTeam".successNel
      } else s"Expected teams nodes:2 Actual ${teams.size}".failureNel
    }

    private def extractScore(node: org.jsoup.nodes.Node): Valid[String] = {
      val table = node.childNodes().get(0).childNode(0)
      val trs = table.childNodes()
      if (trs.size() == 4) {
        val awayChunks = trs.get(1).toString.split("\n")
        val homeChunks = trs.get(3).toString.split("\n")
        val away = Array.fill(awayChunks.length - 2)("")
        val home = Array.fill(homeChunks.length - 2)("")
        Array.copy(awayChunks, 1, away, 0, awayChunks.length - 2)
        Array.copy(homeChunks, 1, home, 0, homeChunks.length - 2)
        val awayLine = away.map {
          _.trim match {
            case Score(d) => d
            case other    => 0
          }
        }.mkString("-")
        val homeLine = home.map {
          _.trim match {
            case Score(d) => d
            case other    => 0
          }
        }.mkString("-")
        s"$awayLine/$homeLine".successNel
      } else s"Expected score nodes:4 Actual ${trs.size}".failureNel
    }

    private def extractPlayers(pls: List[Element]): Valid[List[PlayerLine]] = {
      (pls.:\(List[PlayerLine]()) { (c, acc) =>
        (c.getElementsByTag("td") zip playerKeys)./:(PlayerLine()) { (state, chunkKey) =>
          playerLens(chunkKey._2)(state)(chunkKey._1.toString)
        } :: acc
      }).successNel
    }

    private def extractTotal(total: Element): Valid[Total] = {
      val chunks = total.getElementsByTag("td").toList.drop(2)
      (chunks zip totalKeys)./:(Total()) { (state, chunkKey) =>
        totalLens(chunkKey._2)(state)(chunkKey._1.toString)
      }.successNel
    }

    private def parse(postfixes: List[String], log: LoggingAdapter, dt: (Int, Int, Int)): List[NbaResult] = {
      postfixes.foldLeft(List[NbaResult]()) { (acc, postfix) =>
        val detailsUri = s"http://www.nba.com$postfix"
        val detailsPage = fetchWebPage(detailsUri)

        Try {
          val sections = detailsPage.getElementsByAttributeValue("class", "nbaGIScoreTime Final")
            .get(0)
            .childNodes()

          val container = detailsPage.getElementById("nbaGameInfoContainer")
          log.info(detailsUri)

          val box = container.child(1)
          val awayBox = box.child(1).child(1)
          val homeBox = box.child(2).child(1)

          val homeRows = homeBox.children().toList.tail.tail
          val (homePlayers, homeTotal) = homeRows.splitAt(homeRows.size - 2)
          val awayRows = awayBox.children().toList.tail.tail
          val (awayPlayers, awayTotal) = awayRows.splitAt(awayRows.size - 2)

          val hb = extractPlayers(homePlayers)
          val ab = extractPlayers(awayPlayers)

          val ht = extractTotal(homeTotal.head)
          val at = extractTotal(awayTotal.head)

          val dateTime = extractDateTime(sections.get(0), dt)
          val teams = extractTeams(sections.get(1))
          val scoreLine = extractScore(sections.get(2))

          ((dateTime |@| teams |@| scoreLine |@| hb |@| ab |@| ht |@| at) {
            (dt, tm, scoreLine, hb, ab, ht, at) =>
              val tms = tm.split("/")
              val sc = scoreLine.split("/")
              val sep0 = sc(0).lastIndexOf('-')
              val sep1 = sc(1).lastIndexOf('-')
              NbaResult(awayTeam = tms(0).toLowerCase, homeTeam = tms(1).toLowerCase,
                homeScore = (sc(1).substring(sep1 + 1)).toInt, awayScore = (sc(0).substring(sep0 + 1)).toInt,
                dt = dt.toDate,
                homeScoreBox = sc(1).substring(0, sep1), awayScoreBox = sc(0).substring(0, sep0),
                homeBox = hb, awayBox = ab, homeTotal = ht, awayTotal = at)
          }).fold({ fail: NonEmptyList[String] =>
            log.info("Html parsing errors: {}", fail)
            //if error return nothing, timeout to rescue
            throw new CrawlerException(new Exception(fail.toString), detailsUri)
          }, {
            _ :: acc
          })
        }.getOrElse {
          log.info(s"Something is wrong with $detailsUri. Skip it for now")
          acc
        }
      }
    }

    override def crawl(url: String, teams: Seq[String], log: LoggingAdapter): List[NbaResult] = {
      val webPage = fetchWebPage(url)
      val games = for {
        root ← Option(webPage.getElementById("nbaSSOuter"))
      } yield root.getElementsByClass("GameLine")

      log.info("Games {} number:{}", url, games.map(_.size()).getOrElse(0))
      games.fold(throw new CrawlerException(new Exception("Couldn't find nbaSSOuter/GameLine"), url)) { g =>
        val urls = extractDetailUrls(
          g.flatMap { el =>
            el.getElementsByAttributeValue("class", "nbaActionBar")
              .select("a[href]")
              .filter(_.toString.contains("recapAnc"))
          }.toList
        )

        urls match {
          case scalaz.Success(urls) =>
            parse(urls.toList.asInstanceOf[List[String]], log, (url match { case Date(y, m, d) ⇒ (y.toInt, m.toInt, d.toInt) }))

          case scalaz.Failure(errors) =>
            //If error occurs send nothing
            log.info(errors.toList.mkString("\n"))
            throw new CrawlerException(new Exception("Error"), url)
        }
      }
    }
  }

  case class CrawlerException(cause: Throwable, url: String) extends Exception(cause)

  @implicitNotFound(msg = "Cannot find Crawler type class for ${T}")
  def apply[T <: CrawlPeriod: Crawler] = implicitly[Crawler[T]]
}

trait Crawler[T <: CrawlPeriod] extends RegexSupport {
  import Crawler._

  protected val TIMEOUT = 7000

  type Valid[T] = ValidationNel[String, T]
  type ValidDate = ValidationNel[String, DateTime]

  protected def fetchWebPage(url: String) = try {
    Jsoup.connect(url).ignoreHttpErrors(true).timeout(TIMEOUT).get
  } catch {
    case e: Throwable ⇒ throw new CrawlerException(e, url)
  }

  def crawl(url: String, teams: Seq[String], log: LoggingAdapter): List[NbaResult]
}
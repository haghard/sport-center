package crawler

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import microservice.crawler.NbaResult
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

import scala.collection.JavaConversions._
import scala.concurrent.forkjoin.ThreadLocalRandom

object WebGetter {
  val NAME = """<div(.)+>\s*(\w+)\s*</div>""".r
  val SCORE = """<div(.)+>\s*(\d+)\s*</div>""".r
  val Q_SCORE = """<td class="score">(\d{1,3})</td>""".r
  val TP = """<div class="nbaFnlStatTxSm">\s*(\d{1,2}:\d{1,2})\s*(pm|am)\s*et\s*</div>""".r
  val DATE_EXTRACTOR = """http://www.nba.com/gameline/(\d{4})(\d{2})(\d{2})/""".r
  val TIMEOUT = 5000

  case class CrawlerException(sender: ActorRef, cause: Throwable, url: String) extends Exception(cause)

  val webDispatcher = "akka.io-dispatcher"

  def props(teams: Seq[String]) = Props(new WebGetter(teams)).withDispatcher(webDispatcher)
}

class WebGetter(teams: Seq[String]) extends Actor with ActorLogging {
  import crawler.WebGetter._

  override def postRestart(reason: Throwable) =
    log.info("WebGetter {} was restarted ", self.path)

  override def receive: Receive = {
    case url: String ⇒ parse(url, sender())
  }

  private def parse(url: String, sender: ActorRef) = {
    import com.github.nscala_time.time.Imports._

    val document = try {
      //if (ThreadLocalRandom.current.nextInt(10) > 5) throw new Exception(s"WebGetter error")
      Jsoup.connect(url).ignoreHttpErrors(true).timeout(TIMEOUT).get
    } catch {
      case e ⇒ throw new CrawlerException(sender, e, url)
    }

    val games = for {
      root ← Option(document.getElementById("nbaSSOuter"))
    } yield {
      root.getElementsByClass("Final").toList ::: root.getElementsByClass("Recap").toList :::
        root.getElementsByClass("RecapOT").toList
    }

    val gamesElements = games.getOrElse(List[Element]())
    val pageList = gamesElements.foldLeft(List[NbaResult]()) { (acc, c) ⇒
      val timeElement = c.getElementsByClass("nbaFnlStatTxSm").toList.headOption
      val gameTime = timeElement.map(_.toString).collect({ case TP(t, ampm) ⇒ t + ":" + ampm })
      val time = gameTime.map(_.split(":"))
        .flatMap(array ⇒ if (array.length == 3) Some(array) else None)
        .flatMap { ar ⇒
          val d = Some(url).collect { case DATE_EXTRACTOR(y, m, d) ⇒ (y.toInt, m.toInt, d.toInt) }.head
          val dt0 = new DateTime().withZone(DateTimeZone.forOffsetHours(-5)).withDate(d._1, d._2, d._3)
          val gameDt = if (ar(2) == "am") {
            if (ar(0).toInt == 12) dt0.withTime(0, ar(1).toInt, 0, 0)
            else dt0.withTime(ar(0).toInt, ar(1).toInt, 0, 0)
          } else {
            if (ar(0).toInt == 12) dt0.withTime(12, ar(1).toInt, 0, 0)
            else dt0.withTime(ar(0).toInt + 12, ar(1).toInt, 0, 0)
          }

          Some(gameDt)
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

      (for {
        t ← time
        a ← awayTeamNameAndScore
        h ← homeTeamNameAndScore
      } yield {
        acc :+ NbaResult(h._1, h._2, a._1, a._2, t.toDate)
      }).getOrElse(acc)
    }

    log.info("WebGetter {} complete with size {}", url, pageList.size)
    sender ! (url, pageList)
  }
}
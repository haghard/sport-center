package crawler

import org.joda.time.DateTime
import crawler.writer.Crawler
import crawler.writer.Crawler.{ From2015, From2012 }
import akka.actor.{ Actor, ActorLogging, ActorRef, Props }

object WebGetter {

  val DATE_EXTRACTOR = """http://www.nba.com/gameline/(\d{4})(\d{2})(\d{2})/""".r

  case class WebGetterException(message: String, url: String) extends Exception(message)

  val webDispatcher = "akka.io-dispatcher"

  def props(teams: Seq[String]): Props =
    Props(new WebGetter(teams)).withDispatcher(webDispatcher)
}

class WebGetter(teams: Seq[String]) extends Actor with ActorLogging with RegexSupport {
  import crawler.WebGetter._

  private val from2012to2015 = new DateTime()
    .withZone(microservice.crawler.JODA_EST)
    .withDate(2015, 10, 26).withTime(23, 59, 59, 0)

  override def postRestart(reason: Throwable) =
    log.info("WebGetter {} has been restarted ", self.path)

  override def receive: Receive = {
    case url: String ⇒ parse(url, sender())
  }

  private def parse(url: String, sender: ActorRef) = {
    val dateTime = url match {
      case DATE_EXTRACTOR(y, m, d) ⇒ new DateTime().withZone(microservice.crawler.JODA_EST)
        .withDate(y.toInt, m.toInt, d.toInt).withTime(23, 59, 59, 0)
      case other => throw new WebGetterException("Parse error date in url", url)
    }

    val acc = if (dateTime isBefore from2012to2015) (Crawler[From2012] crawl (url, teams, log))
    else (Crawler[From2015] crawl (url, teams, log))

    log.info("WebGetter for [{}] got back with results:{}", url, acc.size)
    sender ! (url, acc)
  }
}
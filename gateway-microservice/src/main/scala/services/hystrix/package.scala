package services

import java.io.InputStream
import akka.actor.ActorRef
import akka.http.scaladsl.model.HttpEntity.Strict
import akka.util.ByteString
import com.netflix.hystrix.HystrixCommand.Setter
import com.netflix.hystrix._
import akka.http.scaladsl.model.{ HttpHeader, MediaTypes, HttpResponse }
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers._

import scala.collection.immutable

package object hystrix {

  def command(prefix: String, replyTo: ActorRef, uri: String, headers: immutable.Seq[HttpHeader]) =
    mapper(prefix)(replyTo, uri, headers)

  //put it into config
  private val mapper: (String) => (ActorRef, String, immutable.Seq[HttpHeader]) => HystrixCommand[Unit] =
    prefix =>
      { (replyTo: ActorRef, uri: String, headers: immutable.Seq[HttpHeader]) =>
        prefix match {
          case "/api/results/(.*)" => GetResultsByDateCommand(replyTo, uri, headers)
          case "/api/results/(.*)/last" => GetResultsLastCommand(replyTo, uri, headers)
          case "/api/standings/(.*)" => GetStandingsCommand(replyTo, uri, headers)
          case "/api/crawler" => GetSomeColdResultsCommand(replyTo, uri, headers)
          case other => GetSomeColdResultsCommand(replyTo, uri, headers) //default
        }
      }

  private[hystrix] trait WsCall {
    mixin: HystrixCommand[Unit] {
      def replyTo: ActorRef
      def uri: String
      def headers: immutable.Seq[HttpHeader]
    } =>

    import java.net.{ HttpURLConnection, URL }

    private val method = "GET"
    private val TimedOut = "ResponseTimedOut"
    private val FailedEx = "FailedExecution"
    private val ShortCircuited = "ShortCircuited"

    val errorCode = "Server returned HTTP response code: (.\\d+) for URL:(.+)".r

    protected def cause() = {
      if (this.isResponseTimedOut) TimedOut
      else if (this.isFailedExecution) FailedEx
      else if (this.isResponseShortCircuited) ShortCircuited
      else "Unknown"
    }

    //executed in hystrix-api-gateway-pool-n
    //we don't need to set timeout since we have hystrix
    override def run(): Unit = {
      var connection: HttpURLConnection = null
      var inputStream: InputStream = null
      import scala.io.Source
      try {
        connection = (new URL(uri)).openConnection.asInstanceOf[HttpURLConnection]
        connection.setRequestMethod(method)
        connection.setDoInput(true)
        connection.setDoOutput(true)
        //connection.addRequestProperty("Cookie", s"${c.name}=${c.value}")
        headers.foreach { c => connection.setRequestProperty(c.name, c.value) }
        inputStream = connection.getInputStream

        //import scala.collection.JavaConverters._
        //val map = connection.getHeaderFields().asScala
        //val location = connection.getHeaderField("Location")

        /*val outHeaders = map.foldLeft(immutable.Seq[RawHeader]()) { (acc, c) =>
          if ((c._1 ne null) && (c._2.get(0) ne null))
            acc :+ RawHeader(c._1, c._2.get(0))
          else acc
        }*/

        replyTo ! HttpResponse(
          OK, /*headers = immutable.Seq(Location(location)),*/
          entity = Strict(MediaTypes.`application/json`, ByteString(Source.fromInputStream(inputStream).mkString))
        )

      } catch {
        case e: Exception =>
          e.getMessage match {
            case errorCode(code, url) if (code.trim.toInt == Forbidden.intValue) =>
              replyTo ! HttpResponse(Forbidden, entity = Strict(MediaTypes.`application/json`, ByteString(s"{forbidden-resource:$uri}")))
          }

          //notify hystrix on failure
          throw e
      } finally {
        if (inputStream != null) inputStream.close
        if (connection != null) connection.disconnect
      }
    }

    //executed in thread named as HystrixTimer-n
    override def getFallback: Unit = {
      //import scala.collection._
      replyTo ! HttpResponse(ServiceUnavailable, headers = immutable.Seq(Location(uri), RawHeader("isCircuitBreakerOpen", s"$isCircuitBreakerOpen")),
        entity = "Underling api unavailable:" + cause())
    }
  }

  object GetStandingsCommand {
    private val rollingStatisticalWindowInMills = 30000
    private val circuitBreakerSleepWindow = 5000
    private val circuitBreakerErrorThresholdPercentage = 40

    //~ 30 rps/sec
    private val timeoutInMilliseconds = 100
    private val poolSize = 3

    private val key = Setter
      .withGroupKey(HystrixCommandGroupKey.Factory.asKey("GetStandingsCommandKey"))
      .andCommandKey(HystrixCommandKey.Factory.asKey("GetStandingsCommand"))
      .andThreadPoolKey(HystrixThreadPoolKey.Factory.asKey("Get-Standings-pool"))
      .andCommandPropertiesDefaults(
        HystrixCommandProperties.Setter()
          .withExecutionTimeoutInMilliseconds(timeoutInMilliseconds)
          .withExecutionIsolationStrategy(HystrixCommandProperties.ExecutionIsolationStrategy.THREAD)
          .withCircuitBreakerSleepWindowInMilliseconds(circuitBreakerSleepWindow)
          .withCircuitBreakerErrorThresholdPercentage(circuitBreakerErrorThresholdPercentage)
      )
      .andThreadPoolPropertiesDefaults(
        HystrixThreadPoolProperties.Setter()
          .withCoreSize(poolSize)
      )
    //.withMaxQueueSize(1 << 8)
    //.withMetricsRollingStatisticalWindowBuckets(rollingStatisticalWindowInMills)
    //.withMetricsRollingStatisticalWindowBuckets(rollingStatisticalWindowInMills/1000))

    def apply(replyTo: ActorRef, uri: String, headers: immutable.Seq[HttpHeader]) = new GetStandingsCommand(replyTo, uri, headers)
  }

  object GetResultsLastCommand {
    private val rollingStatisticalWindowInMills = 30000
    private val circuitBreakerSleepWindow = 5000
    private val circuitBreakerErrorThresholdPercentage = 40

    //~ 50 rps/sec
    private val timeoutInMilliseconds = 100
    private val poolSize = 5

    private val key = Setter
      .withGroupKey(HystrixCommandGroupKey.Factory.asKey("GetLastResultsCommandKey"))
      .andCommandKey(HystrixCommandKey.Factory.asKey("GetLastResultsCommand"))
      .andThreadPoolKey(HystrixThreadPoolKey.Factory.asKey("Get-Last-Results-pool"))
      .andCommandPropertiesDefaults(
        HystrixCommandProperties.Setter()
          .withExecutionTimeoutInMilliseconds(timeoutInMilliseconds)
          .withExecutionIsolationStrategy(HystrixCommandProperties.ExecutionIsolationStrategy.THREAD)
          .withCircuitBreakerSleepWindowInMilliseconds(circuitBreakerSleepWindow)
          .withCircuitBreakerErrorThresholdPercentage(circuitBreakerErrorThresholdPercentage)
      )
      .andThreadPoolPropertiesDefaults(
        HystrixThreadPoolProperties.Setter()
          .withCoreSize(poolSize)
      )
    //.withMetricsRollingStatisticalWindowInMilliseconds(rollingStatisticalWindowInMills)
    //.withMetricsRollingStatisticalWindowBuckets(rollingStatisticalWindowInMills/1000))

    def apply(replyTo: ActorRef, uri: String, headers: immutable.Seq[HttpHeader]) =
      new GetResultsLastCommand(replyTo, uri, headers)
  }

  object GetResultsByDateCommand {
    private val rollingStatisticalWindowInMills = 30000
    private val circuitBreakerSleepWindow = 5000
    private val circuitBreakerErrorThresholdPercentage = 40

    //~ 50 rps/sec
    private val timeoutInMilliseconds = 100
    private val poolSize = 5

    private val key = Setter
      .withGroupKey(HystrixCommandGroupKey.Factory.asKey("GetResultsByDateCommandKey"))
      .andCommandKey(HystrixCommandKey.Factory.asKey("GetResultsByDateCommand"))
      .andThreadPoolKey(HystrixThreadPoolKey.Factory.asKey("Get-Results-By-Date-pool"))
      .andCommandPropertiesDefaults(
        HystrixCommandProperties.Setter()
          .withExecutionTimeoutInMilliseconds(timeoutInMilliseconds)
          .withExecutionIsolationStrategy(HystrixCommandProperties.ExecutionIsolationStrategy.THREAD)
          .withCircuitBreakerSleepWindowInMilliseconds(circuitBreakerSleepWindow)
          .withCircuitBreakerErrorThresholdPercentage(circuitBreakerErrorThresholdPercentage)
      )
      .andThreadPoolPropertiesDefaults(
        HystrixThreadPoolProperties.Setter()
          .withCoreSize(poolSize)
      )
    //.withMetricsRollingStatisticalWindowInMilliseconds(rollingStatisticalWindowInMills)
    //.withMetricsRollingStatisticalWindowBuckets(rollingStatisticalWindowInMills/1000))

    def apply(replyTo: ActorRef, uri: String, headers: immutable.Seq[HttpHeader]) =
      new GetResultsByDateCommand(replyTo, uri, headers)
  }

  object GetSomeColdResultsCommand {
    private val rollingStatisticalWindowInMills = 30000
    private val circuitBreakerSleepWindow = 5000
    private val circuitBreakerErrorThresholdPercentage = 40

    //~ 15 rps/sec
    private val timeoutInMilliseconds = 200
    private val poolSize = 3

    private val key = Setter
      .withGroupKey(HystrixCommandGroupKey.Factory.asKey("GetCrawlerResultsCommandKey"))
      .andCommandKey(HystrixCommandKey.Factory.asKey("GetCrawlerResultsCommand"))
      .andThreadPoolKey(HystrixThreadPoolKey.Factory.asKey("Get-Crawler-Results-pool"))
      .andCommandPropertiesDefaults(
        HystrixCommandProperties.Setter()
          .withExecutionTimeoutInMilliseconds(timeoutInMilliseconds)
          .withExecutionIsolationStrategy(HystrixCommandProperties.ExecutionIsolationStrategy.THREAD)
          .withCircuitBreakerSleepWindowInMilliseconds(circuitBreakerSleepWindow)
          .withCircuitBreakerErrorThresholdPercentage(circuitBreakerErrorThresholdPercentage)
      )
      .andThreadPoolPropertiesDefaults(
        HystrixThreadPoolProperties.Setter()
          .withCoreSize(poolSize)
      )
    //.withMetricsRollingStatisticalWindowInMilliseconds(rollingStatisticalWindowInMills)
    //.withMetricsRollingStatisticalWindowBuckets(rollingStatisticalWindowInMills/1000))

    def apply(replyTo: ActorRef, uri: String, headers: immutable.Seq[HttpHeader]) = new GetSomeColdResultsCommand(replyTo, uri, headers)
  }

  private[hystrix] class GetResultsByDateCommand(val replyTo: ActorRef, val uri: String, val headers: immutable.Seq[HttpHeader])
    extends HystrixCommand[Unit](GetResultsByDateCommand.key)
    with WsCall

  private[hystrix] class GetStandingsCommand(val replyTo: ActorRef, val uri: String, val headers: immutable.Seq[HttpHeader])
    extends HystrixCommand[Unit](GetStandingsCommand.key)
    with WsCall

  private[hystrix] class GetResultsLastCommand(val replyTo: ActorRef, val uri: String, val headers: immutable.Seq[HttpHeader])
    extends HystrixCommand[Unit](GetResultsLastCommand.key)
    with WsCall

  private[hystrix] class GetSomeColdResultsCommand(val replyTo: ActorRef, val uri: String, val headers: immutable.Seq[HttpHeader])
    extends HystrixCommand[Unit](GetSomeColdResultsCommand.key)
    with WsCall
}
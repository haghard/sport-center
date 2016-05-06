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

  private val mapper: (String) => (ActorRef, String, immutable.Seq[HttpHeader]) => HystrixCommand[Unit] =
    prefix =>
      { (replyTo: ActorRef, uri: String, headers: immutable.Seq[HttpHeader]) =>
        prefix match {
          case "/api/results/(.*)"      => GetResultsByDateCommand(replyTo, uri, headers)
          case "/api/results/(.*)/last" => GetResultsLastCommand(replyTo, uri, headers)
          case "/api/standings/(.*)"    => GetStandingsCommand(replyTo, uri, headers)
          case "/api/crawler"           => GetSomeColdResultsCommand(replyTo, uri, headers)
          case other                    => GetSomeColdResultsCommand(replyTo, uri, headers) //default
        }
      }

  private[hystrix] trait BlockingCall {
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
    //we don't need timeout since we have hystrix
    override def run(): Unit = {
      var connection: HttpURLConnection = null
      var inputStream: InputStream = null

      try {
        connection = (new URL(uri)).openConnection.asInstanceOf[HttpURLConnection]
        connection.setRequestMethod(method)
        connection.setDoInput(true)
        connection.setDoOutput(true)
        headers.foreach { c =>
          connection.setRequestProperty(c.name, c.value)
          //connection.addRequestProperty("Cookie", s"${c.name}=${c.value}")
        }
        inputStream = connection.getInputStream
        replyTo ! HttpResponse(OK, entity = Strict(MediaTypes.`application/json`,
          ByteString(scala.io.Source.fromInputStream(inputStream).mkString)))
      } catch {
        case e: Exception =>
          e.getMessage match {
            case errorCode(code, url) if (code.trim.toInt == 403) =>
              replyTo ! HttpResponse(Forbidden, entity = Strict(MediaTypes.`application/json`, ByteString(s"{ forbidden-url: $uri }")))
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
      import scala.collection._

      replyTo ! HttpResponse(ServiceUnavailable, headers = immutable.Seq(
        RawHeader("Target", uri), RawHeader("isCircuitBreakerOpen", s"$isCircuitBreakerOpen")),
        entity = "Underling api unavailable cause: " + cause())
    }
  }

  object GetStandingsCommand {
    private val circuitBreakerSleepWindow = 5000
    private val circuitBreakerErrorThresholdPercentage = 40

    //~ 30 rps/sec
    private val timeoutInMilliseconds = 100
    private val poolSize = 3

    private val key = Setter
      .withGroupKey(HystrixCommandGroupKey.Factory.asKey("GetStandingsCommandKey"))
      .andCommandKey(HystrixCommandKey.Factory.asKey("GetStandingsCommand"))
      .andThreadPoolKey(HystrixThreadPoolKey.Factory.asKey("get-standings-command-pool"))
      .andCommandPropertiesDefaults(
        HystrixCommandProperties.Setter()
          .withExecutionTimeoutInMilliseconds(timeoutInMilliseconds)
          .withExecutionIsolationStrategy(HystrixCommandProperties.ExecutionIsolationStrategy.THREAD)
          .withCircuitBreakerSleepWindowInMilliseconds(circuitBreakerSleepWindow)
          .withCircuitBreakerErrorThresholdPercentage(circuitBreakerErrorThresholdPercentage))
      .andThreadPoolPropertiesDefaults(HystrixThreadPoolProperties.Setter().withCoreSize(poolSize))

    def apply(replyTo: ActorRef, uri: String, headers: immutable.Seq[HttpHeader]) = new GetStandingsCommand(replyTo, uri, headers)
  }

  object GetResultsLastCommand {
    private val circuitBreakerSleepWindow = 5000
    private val circuitBreakerErrorThresholdPercentage = 40

    //~ 50 rps/sec
    private val timeoutInMilliseconds = 100
    private val poolSize = 5

    private val key = Setter
      .withGroupKey(HystrixCommandGroupKey.Factory.asKey("GetLastResultsCommandKey"))
      .andCommandKey(HystrixCommandKey.Factory.asKey("GetLastResultsCommand"))
      .andThreadPoolKey(HystrixThreadPoolKey.Factory.asKey("get-last-results-pool"))
      .andCommandPropertiesDefaults(
        HystrixCommandProperties.Setter()
          .withExecutionTimeoutInMilliseconds(timeoutInMilliseconds)
          .withExecutionIsolationStrategy(HystrixCommandProperties.ExecutionIsolationStrategy.THREAD)
          .withCircuitBreakerSleepWindowInMilliseconds(circuitBreakerSleepWindow)
          .withCircuitBreakerErrorThresholdPercentage(circuitBreakerErrorThresholdPercentage))
      .andThreadPoolPropertiesDefaults(HystrixThreadPoolProperties.Setter().withCoreSize(poolSize))

    def apply(replyTo: ActorRef, uri: String, headers: immutable.Seq[HttpHeader]) =
      new GetResultsLastCommand(replyTo, uri, headers)
  }

  object GetResultsByDateCommand {
    private val circuitBreakerSleepWindow = 5000
    private val circuitBreakerErrorThresholdPercentage = 40

    //~ 50 rps/sec
    private val timeoutInMilliseconds = 100
    private val poolSize = 5

    private val key = Setter
      .withGroupKey(HystrixCommandGroupKey.Factory.asKey("GetResultsByDateCommandKey"))
      .andCommandKey(HystrixCommandKey.Factory.asKey("GetResultsByDateCommand"))
      .andThreadPoolKey(HystrixThreadPoolKey.Factory.asKey("get-results-by-date-pool"))
      .andCommandPropertiesDefaults(
        HystrixCommandProperties.Setter()
          .withExecutionTimeoutInMilliseconds(timeoutInMilliseconds)
          .withExecutionIsolationStrategy(HystrixCommandProperties.ExecutionIsolationStrategy.THREAD)
          .withCircuitBreakerSleepWindowInMilliseconds(circuitBreakerSleepWindow)
          .withCircuitBreakerErrorThresholdPercentage(circuitBreakerErrorThresholdPercentage))
      .andThreadPoolPropertiesDefaults(HystrixThreadPoolProperties.Setter().withCoreSize(poolSize))

    def apply(replyTo: ActorRef, uri: String, headers: immutable.Seq[HttpHeader]) =
      new GetResultsByDateCommand(replyTo, uri, headers)
  }

  object GetSomeColdResultsCommand {
    private val circuitBreakerSleepWindow = 5000
    private val circuitBreakerErrorThresholdPercentage = 40

    private val timeoutInMilliseconds = 200
    private val poolSize = 3

    private val key = Setter
      .withGroupKey(HystrixCommandGroupKey.Factory.asKey("GetCrawlerResultsCommandKey"))
      .andCommandKey(HystrixCommandKey.Factory.asKey("GetCrawlerResultsCommand"))
      .andThreadPoolKey(HystrixThreadPoolKey.Factory.asKey("get-crawler-results-pool"))
      .andCommandPropertiesDefaults(
        HystrixCommandProperties.Setter()
          .withExecutionTimeoutInMilliseconds(timeoutInMilliseconds)
          .withExecutionIsolationStrategy(HystrixCommandProperties.ExecutionIsolationStrategy.THREAD)
          .withCircuitBreakerSleepWindowInMilliseconds(circuitBreakerSleepWindow)
          .withCircuitBreakerErrorThresholdPercentage(circuitBreakerErrorThresholdPercentage))
      .andThreadPoolPropertiesDefaults(HystrixThreadPoolProperties.Setter().withCoreSize(poolSize))

    def apply(replyTo: ActorRef, uri: String, headers: immutable.Seq[HttpHeader]) = new GetSomeColdResultsCommand(replyTo, uri, headers)
  }

  private[hystrix] class GetResultsByDateCommand(val replyTo: ActorRef, val uri: String, val headers: immutable.Seq[HttpHeader])
    extends HystrixCommand[Unit](GetResultsByDateCommand.key)
    with BlockingCall

  private[hystrix] class GetStandingsCommand(val replyTo: ActorRef, val uri: String, val headers: immutable.Seq[HttpHeader])
    extends HystrixCommand[Unit](GetStandingsCommand.key)
    with BlockingCall

  private[hystrix] class GetResultsLastCommand(val replyTo: ActorRef, val uri: String, val headers: immutable.Seq[HttpHeader])
    extends HystrixCommand[Unit](GetResultsLastCommand.key)
    with BlockingCall

  private[hystrix] class GetSomeColdResultsCommand(val replyTo: ActorRef, val uri: String, val headers: immutable.Seq[HttpHeader])
    extends HystrixCommand[Unit](GetSomeColdResultsCommand.key)
    with BlockingCall
}
package http

import akka.http.scaladsl.marshalling.{ Marshaller, ToResponseMarshaller }
import akka.http.scaladsl.model._
import akka.stream.scaladsl.Source
import scala.concurrent.ExecutionContext
import akka.http.scaladsl.model.{ HttpCharsets, MediaType }

trait SSEventsMarshalling {
  type ToMessage[A] = A ⇒ SSEvents.Message

  /**
   * Media type for Server-Sent Events as required by the
   * [[http://www.w3.org/TR/eventsource/#event-stream-interpretation SSE specification]].
   */
  val `text/event-stream`: MediaType.WithFixedCharset = MediaType.customWithFixedCharset(
    "text",
    "event-stream",
    HttpCharsets.`UTF-8`
  )

  def messageToResponseMarshaller[A: ToMessage, B](implicit ec: ExecutionContext): ToResponseMarshaller[Source[A, B]] =
    Marshaller.withFixedContentType(`text/event-stream`) { messages =>
      val data = messages.map(_.toByteString)
      HttpResponse(entity = HttpEntity.CloseDelimited(`text/event-stream`, data))
    }
}


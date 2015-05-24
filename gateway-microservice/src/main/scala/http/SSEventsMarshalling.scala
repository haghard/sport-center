package http

import akka.http.scaladsl.model._
import akka.stream.scaladsl.Source
import akka.http.scaladsl.marshalling.{ Marshaller, ToResponseMarshaller }

import scala.concurrent.ExecutionContext

trait SSEventsMarshalling {
  type ToMessage[A] = A ⇒ SSEvents.Message

  /**
   * SSE content type
   * [[http://www.w3.org/TR/eventsource/#event-stream-interpretation SSE specification]].
   */
  private[this] val `text/event-stream` = MediaType.custom("text/event-stream", akka.http.scaladsl.model.MediaType.Encoding.Open)

  def messageToResponseMarshaller[A: ToMessage, B](implicit ec: ExecutionContext): ToResponseMarshaller[Source[A, B]] =
    Marshaller.withFixedCharset(`text/event-stream`, HttpCharsets.`UTF-8`) { messages ⇒
      HttpResponse(entity = HttpEntity.CloseDelimited(`text/event-stream`, messages.map(_.toByteString)))
    }
}


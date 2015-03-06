package http

import akka.http.marshalling._
import akka.http.model.{ HttpEntity, HttpResponse, HttpCharsets, MediaType }
import akka.stream.scaladsl.Source

import scala.concurrent.ExecutionContext

trait SSEventsMarshalling {
  type ToMessage[A] = A ⇒ SSEvents.Message

  /**
   * SSE content type
   * [[http://www.w3.org/TR/eventsource/#event-stream-interpretation SSE specification]].
   */
  private[this] val `text/event-stream` = MediaType.custom("text", "event-stream")

  def messageToResponseMarshaller[A: ToMessage, B](implicit ec: ExecutionContext): ToResponseMarshaller[Source[A, B]] =
    Marshaller.withFixedCharset(`text/event-stream`, HttpCharsets.`UTF-8`) { messages ⇒
      HttpResponse(entity = HttpEntity.CloseDelimited(`text/event-stream`,
        messages.mapMaterialized(_ => ()).map(_.toByteString)))
    }
}


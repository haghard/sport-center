package microservice.http

import akka.http.marshalling.{ PredefinedToEntityMarshallers, ToEntityMarshaller => TEM }
import akka.http.model.{ ContentTypeRange, MediaRange, MediaTypes }
import akka.http.unmarshalling.Unmarshaller.UnsupportedContentTypeException
import akka.http.unmarshalling.{ FromEntityUnmarshaller => FEUM, PredefinedFromEntityUnmarshallers }
import akka.http.util.FastFuture
import akka.stream.FlowMaterializer
import spray.json._

import scala.concurrent.ExecutionContext

trait SprayJsonMarshalling {

  implicit def feum[A](implicit reader: RootJsonReader[A], m: FlowMaterializer, ec: ExecutionContext): FEUM[A] =
    PredefinedFromEntityUnmarshallers.stringUnmarshaller.flatMapWithInput { (entity, s) =>
      if (entity.contentType.mediaType == MediaTypes.`application/json`)
        FastFuture.successful(reader.read(JsonParser(s)))
      else
        FastFuture.failed(UnsupportedContentTypeException(ContentTypeRange(MediaRange(MediaTypes.`application/json`))))
    }

  implicit def tem[A](implicit writer: RootJsonWriter[A], printer: JsonPrinter = PrettyPrinter): TEM[A] = {
    val stringMarshaller = PredefinedToEntityMarshallers.stringMarshaller(MediaTypes.`application/json`)
    stringMarshaller.compose(printer).compose(writer.write)
  }
}

import akka.contrib.datareplication.LWWMap
import akka.util.ByteString
import discovery.ServiceDiscovery.DiscoveryLine

import scala.annotation.tailrec

package object http {

  import spray.json._

  implicit def metrics2Message(metrics: Vector[String]) = {
    metrics.toList match {
      case List(n) => SSEvents.Element(n)
      case h :: t  => SSEvents.Component(h :: t)
    }
  }

  implicit def mapToMessage(replica: LWWMap) =
    SSEvents.Element(replica.entries.asInstanceOf[Map[String, DiscoveryLine]].values.toList.toJson.prettyPrint)

  object SSEvents {

    trait Message {
      def toByteString: ByteString
    }

    val encoding = "UTF-8"

    final case class Component(messages: List[String]) extends Message {
      override def toByteString = messages
        .map(x => ByteString(SSEvents.Element(x).toString, encoding))
        .reduce((f, s) => f ++ s)
    }

    final case class Element(val data: String, event: Option[String] = None) extends Message {
      require(event.forall(_.forall(c ⇒ c != '\n' && c != '\r')), "Event must not contain \\n or \\r!")

      /**
       * Convert to a `java.lang.String`
       * according to the [[http://www.w3.org/TR/eventsource/#event-stream-interpretation SSE specification]].
       * @return message converted to `java.lang.String`
       */
      override def toString: String = {
        @tailrec def addLines(builder: StringBuilder, label: String, s: String, index: Int): StringBuilder = {
          @tailrec def addLine(index: Int): Int =
            if (index >= s.length)
              -1
            else {
              val c = s.charAt(index)
              builder.append(c)
              if (c == '\n') index + 1
              else addLine(index + 1)
            }

          builder.append(label)
          addLine(index) match {
            case -1    ⇒ builder.append('\n')
            case index ⇒ addLines(builder, label, s, index)
          }
        }

        def addData(builder: StringBuilder): StringBuilder =
          addLines(builder, "data:", data, 0).append('\n')

        def addEvent(builder: StringBuilder): StringBuilder =
          event match {
            case Some(e) ⇒ addLines(builder, "event:", e, 0)
            case None    ⇒ builder
          }

        def newBuilder(): StringBuilder = {
          // Public domain algorithm: http://graphics.stanford.edu/~seander/bithacks.html#RoundUpPowerOf2
          // We want powers of two both because they typically work better with the allocator,
          // and because we want to minimize reallocations/buffer growth.
          def nextPowerOfTwoBiggerThan(i: Int): Int = {
            var v = i
            v -= 1
            v |= v >> 1
            v |= v >> 2
            v |= v >> 4
            v |= v >> 8
            v |= v >> 16
            v + 1
          }
          // Why 8? "data:" == 5 + \n\n (1 data (at least) and 1 ending) == 2 and then we add 1 extra to allocate
          //        a bigger memory slab than data.length since we're going to add data ("data:" + "\n") per line
          // Why 7? "event:" + \n == 7 chars
          new StringBuilder(nextPowerOfTwoBiggerThan(8 + data.length + event.fold(0)(_.length + 7)))
        }

        addData(addEvent(newBuilder())).toString()
      }

      /**
       * Convert to an `akka.util.ByteString`
       * according to the [[http://www.w3.org/TR/eventsource/#event-stream-interpretation SSE specification]].
       * @return message converted to UTF-8 encoded `akka.util.ByteString`
       */
      def toByteString: ByteString = ByteString(toString, encoding)
    }
  }
}

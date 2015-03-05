package microservice

import scalaz.\/

package object domain {

  trait State

  trait DomainEvent

  trait Command

  trait QueryCommand

  trait CommandValidator[T] {
    def validate(cmd: Command): String \/ (Long, T)
  }

  trait EventPublisher {
    def updateState(e: DomainEvent): Unit
  }

  implicit class ListExtensions[K](val list: List[K]) extends AnyVal {
    def copyWithout(item: K) = {
      val (left, right) = list span (_ != item)
      left ::: right.drop(1)
    }
  }
}

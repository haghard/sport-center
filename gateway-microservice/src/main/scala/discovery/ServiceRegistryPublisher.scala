package discovery

import akka.actor.{ ActorLogging, Props }
import akka.contrib.datareplication.LWWMap
import akka.contrib.datareplication.Replicator.Changed
import akka.stream.actor.{ ActorPublisher, ActorPublisherMessage }

object ServiceRegistryPublisher {

  def props(dispatcher: String): Props =
    Props(new ServiceRegistryPublisher).withDispatcher(dispatcher)
}

class ServiceRegistryPublisher extends ActorPublisher[LWWMap] with ActorLogging {

  ServiceDiscovery(context.system).subscribe(self)

  override def receive: Receive = {
    case Changed(key, replica) if (isActive && totalDemand > 0 && replica.isInstanceOf[LWWMap]) ⇒
      val map = replica.asInstanceOf[LWWMap]
      onNext(map)

    case ActorPublisherMessage.Request(n) ⇒

    case ActorPublisherMessage.SubscriptionTimeoutExceeded ⇒
      onComplete()
      context.stop(self)

    case ActorPublisherMessage.Cancel ⇒
      onComplete()
      context.stop(self)
  }
}

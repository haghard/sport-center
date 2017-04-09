package discovery

import akka.actor.{ ActorLogging, Props }
import akka.cluster.ddata.{ LWWMapKey, LWWMap }
import akka.cluster.ddata.Replicator.Changed
import akka.stream.actor.{ ActorPublisher, ActorPublisherMessage }
import discovery.ReplicatedHttpRoutes.HttpRouteLine

object ServiceRegistryPublisher {
  def props(dispatcher: String): Props =
    Props(new ServiceRegistryPublisher).withDispatcher(dispatcher)
}

class ServiceRegistryPublisher extends ActorPublisher[LWWMap[HttpRouteLine]] with ActorLogging {

  ReplicatedHttpRoutes(context.system).subscribe(self)

  override def receive: Receive = {
    case r @ Changed(LWWMapKey(key)) if (isActive && totalDemand > 0 && r.dataValue.isInstanceOf[LWWMap[HttpRouteLine]]) ⇒
      onNext(r.dataValue.asInstanceOf[LWWMap[HttpRouteLine]])

    case ActorPublisherMessage.Request(n) ⇒

    case ActorPublisherMessage.SubscriptionTimeoutExceeded ⇒
      onComplete()
      context.stop(self)

    case ActorPublisherMessage.Cancel ⇒
      onComplete()
      context.stop(self)
  }
}

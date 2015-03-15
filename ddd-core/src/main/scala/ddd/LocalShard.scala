package ddd

import akka.actor._
import akka.contrib.pattern.ShardRegion.Passivate
import scala.collection.immutable
import scala.reflect.ClassTag

object LocalShard {

  implicit def localShardFactory[T <: BusinessEntity: BusinessEntityActorFactory: ShardResolution: ClassTag](implicit system: ActorSystem): ShardFactory[T] = {
    new ShardFactory[T] {
      override def getOrCreate: ActorRef = {
        system.actorOf(Props(new LocalShardGuardian[T]()), name = shardName)
      }
    }
  }

  trait CreationSupport {
    def getChild(name: String): Option[ActorRef]
    def removeChild(name: String)
    def createChild(props: Props, name: String): ActorRef
    def getOrCreateChild(props: Props, name: String): ActorRef = getChild(name).getOrElse(createChild(props, name))
  }

  trait LocalMapChildCreationSupport extends CreationSupport {
    mixin: ActorLogging { def context: ActorContext } =>
    private var children: immutable.Map[String, ActorRef] = Map()

    override def getChild(name: String): Option[ActorRef] = children.get(name)

    override def removeChild(name: String) =
      children = children - name

    override def createChild(props: Props, name: String): ActorRef = {
      val actor = context.actorOf(props, name)
      log.info(s"Child-actor created $actor, with name $name")
      children = children + (name -> actor)
      actor
    }
  }

  trait AkkaActorsChildCreationSupport extends CreationSupport {
    mixin: ActorLogging =>

    def context: ActorContext

    override def getChild(name: String): Option[ActorRef] = context.child(name)

    override def removeChild(name: String) = {}

    override def createChild(props: Props, name: String): ActorRef = {
      val actor = context.actorOf(props, name)
      log.info(s"Child-actor created $actor, with name $name")
      actor
    }
  }

  final class LocalShardGuardian[A <: BusinessEntity](implicit ct: ClassTag[A],
    resolution: IdResolution[A], childFactory: BusinessEntityActorFactory[A])
      extends /*LocalMapChildCreationSupport*/ AkkaActorsChildCreationSupport
      with Actor with ActorLogging {

    override def aroundReceive(receive: Receive, msg: Any): Unit = {
      receive.applyOrElse(msg match {
        case c: DomainCommand => CommandMessage(c)
        case other            => other
      }, unhandled)
    }

    override def receive: Receive = {
      case Terminated(child) =>
        log.info("Child {} terminated", child.path)
        removeChild(child.path.name)
      case Passivate(stopMessage) => dismiss(sender(), stopMessage)
      case msg: EntityMessage =>
        val childProps = childFactory.props(PassivationConfig(Passivate(PoisonPill), childFactory.inactivityTimeout))
        val child = ::(childProps, toId(msg))
        log.info("Forwarding {} to {}", msg.getClass.getSimpleName, child.path)
        context watch child
        child forward msg
    }

    def toId(msg: Any) = resolution.entityIdResolver(msg)

    def ::(childProps: Props, childId: String) = getOrCreateChild(childProps, childId)

    def dismiss(child: ActorRef, stopMessage: Any) {
      log.info(s"Passivating $child")
      child ! stopMessage
    }
  }
}
package discovery

import akka.cluster.Cluster
import akka.remote.testconductor.RoleName
import akka.remote.testkit.{MultiNodeSpec, MultiNodeConfig}
import com.typesafe.config.ConfigFactory
import discovery.ServiceDiscovery.{UnsetAddress, Registry, KV, SetKey}
import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration._
import scalaz.\/-

/**
 * multi-jvm:test
 * multi-jvm:test-only discovery.ServiceDiscoverySpec
 *
 */
object ServiceDiscoverySpec extends MultiNodeConfig {
  val nodeA = role("node-a")
  val nodeB = role("node-b")
  val nodeC = role("node-c")

  private def standings(ip: String) = s"http://$ip/api/standings"
  
  private def results(ip: String) = s"http://$ip/api/results"
  
  commonConfig(ConfigFactory.parseString("""
    akka.loglevel = INFO
    akka.actor.provider = "akka.cluster.ClusterActorRefProvider"
    akka.log-dead-letters-during-shutdown = off
    akka.contrib.data-replication.gossip-interval = 1 s
    discovery {
      http-port = [8000, 8100]
      ops-timeout = 2 s
    }
  """))
}

class ServiceDiscoverySpec extends MultiNodeSpec(ServiceDiscoverySpec) with STMultiNodeSpec {
  import ServiceDiscoverySpec._
  import scala.concurrent.ExecutionContext.Implicits.global

  private val cluster = Cluster(system)
  
  override def initialParticipants = roles.size

  private def join(from: RoleName, to: RoleName): Unit = {
    runOn(from) {
      cluster join node(to).address
    }
    enterBarrier(from.name + "-joined")
  }
  

  "Demo of a replicated service registry" must {
    "join cluster" in {
      val nodes = List(nodeA, nodeB, nodeC)
      nodes.foreach { join(_, nodeA) }
      enterBarrier("after-join")
    }

    "handle several updates from one node" in within(5 seconds) {
      runOn(nodeB) {
        ServiceDiscovery(system).setKey(SetKey(KV(nodeB.name, standings(nodeB.name))))
        ServiceDiscovery(system).setKey(SetKey(KV(nodeB.name, results(nodeB.name))))
      }

      enterBarrier("updates-done")

      awaitAssert {
        val result = Await.result(ServiceDiscovery(system).findAll, 3 seconds)
        result shouldBe \/-(Registry(mutable.HashMap(nodeB.name -> Set(standings(nodeB.name), results(nodeB.name)))))
      }
      enterBarrier("after-2")
    }

    "handle deleteAll from other node" in within(5 seconds) {
      runOn(nodeA) {
        ServiceDiscovery(system).deleteAll(UnsetAddress(nodeB.name))
      }

      enterBarrier("updates-done")

      awaitAssert {
        val result = Await.result(ServiceDiscovery(system).findAll, 3 seconds)
        result shouldBe \/-(Registry(mutable.HashMap()))
      }
      enterBarrier("after-3")
    }
    
    "handle several updates from diff nodes" in within(5 seconds) {
      runOn(nodeA) {
        ServiceDiscovery(system).setKey(SetKey(KV(nodeA.name, standings(nodeA.name))))
      }
      runOn(nodeB) {
        ServiceDiscovery(system).setKey(SetKey(KV(nodeB.name, results(nodeB.name))))
      }
      runOn(nodeA) {
        ServiceDiscovery(system).setKey(SetKey(KV(nodeA.name, results(nodeA.name))))
      }

      enterBarrier("updates-done")

      awaitAssert {
        val result = Await.result(ServiceDiscovery(system).findAll, 3 seconds)
        result shouldBe \/-(Registry(mutable.HashMap(nodeA.name -> Set(standings(nodeA.name), results(nodeA.name)), 
                                                     nodeB.name -> Set(results(nodeB.name)))))
      }
      enterBarrier("after-4")
    }
  }
}

class ServiceDiscoverySpecMultiJvmNode1 extends ServiceDiscoverySpec
class ServiceDiscoverySpecMultiJvmNode2 extends ServiceDiscoverySpec
class ServiceDiscoverySpecMultiJvmNode3 extends ServiceDiscoverySpec
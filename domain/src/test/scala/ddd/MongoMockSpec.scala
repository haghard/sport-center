/*

package domain

import akka.actor.ActorSystem
import akka.testkit.TestKit
import com.typesafe.config.{ Config, ConfigFactory }
import de.flapdoodle.embed.mongo.config._
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.mongo.{ MongodStarter, Command }
import de.flapdoodle.embed.process.config.io.ProcessOutput
import de.flapdoodle.embed.process.extract.UUIDTempNaming
import de.flapdoodle.embed.process.io.{ NullProcessor, Processors }
import de.flapdoodle.embed.process.io.directories.PlatformTempDir
import de.flapdoodle.embed.process.runtime.Network
import org.scalatest.{ MustMatchers, BeforeAndAfterAll, BeforeAndAfterEach, WordSpecLike }

object MongoMockSpec {

  def config(port: Int) = ConfigFactory.parseString(s"""
    akka {
      persistence {
        journal.plugin = "casbah-journal"
        snapshot-store.plugin = "casbah-snapshot-store"
        at-least-once-delivery.redeliver-interval = 5000ms
      }
    }

    casbah-journal.mongo-journal-url = "mongodb://localhost:$port/store0.messages"
    casbah-snapshot-store.mongo-snapshot-url = "mongodb://localhost:$port/store0.snapshots"
    casbah-journal.mongo-journal-write-concern = "acknowledged"
    casbah-journal.mongo-journal-write-concern-timeout = 5000
  """)

  lazy val freePort = Network.getFreeServerPort
}

abstract class MongoMockSpec(config: Config) extends TestKit(ActorSystem("Domain", config))
    with WordSpecLike
    with MustMatchers
    with BeforeAndAfterEach
    with BeforeAndAfterAll {

  import domain.MongoMockSpec._

  override def beforeAll() {
    mongodExe
  }

  override def afterAll() {
    mongod.stop()
    mongodExe.stop()
    TestKit.shutdownActorSystem(system)
  }

  lazy val host = "localhost"
  lazy val port = freePort
  lazy val localHostIPV6 = Network.localhostIsIPv6()

  val artifactStorePath = new PlatformTempDir()
  val executableNaming = new UUIDTempNaming()
  val command = Command.MongoD
  val version = Version.Main.PRODUCTION

  val processOutput = new ProcessOutput(
    Processors.named("[mongod>]", new NullProcessor),
    Processors.named("[MONGOD>]", new NullProcessor),
    Processors.named("[console>]", new NullProcessor))

  val runtimeConfig =
    new RuntimeConfigBuilder()
      .defaults(command)
      .processOutput(processOutput)
      .artifactStore(new ArtifactStoreBuilder()
        .defaults(command)
        .download(new DownloadConfigBuilder()
          .defaultsForCommand(command)
          .artifactStorePath(artifactStorePath))
        .executableNaming(executableNaming))
      .build()

  val mongodConfig = new MongodConfigBuilder()
    .version(version)
    .net(new Net(port, localHostIPV6))
    .build()

  lazy val mongodStarter = MongodStarter.getInstance(runtimeConfig)
  lazy val mongod = mongodStarter.prepare(mongodConfig)
  lazy val mongodExe = mongod.start()
}*/

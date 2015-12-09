import sbt._

object Dependencies {

  val Scala = "2.11.7"
  val crossScala = Seq(Scala, "2.10.5")

  val Akka = "2.4.1"
  val AkkaStreamsVersion = "2.0-M1"
  val Hystrix = "1.4.14"

  implicit class Exclude(module: ModuleID) {
    def akkaExclude: ModuleID = module
      .excludeAll(ExclusionRule("com.typesafe"))
      .excludeAll(ExclusionRule("org.slf4j"))
  }

  object akka {
    val actor                 = "com.typesafe.akka"       %%    "akka-actor"                    % Akka withSources()
    val cluster               = "com.typesafe.akka"       %%    "akka-cluster"                  % Akka withSources()

    val sharding              = "com.typesafe.akka"       %%    "akka-cluster-sharding"         %  Akka withSources()
    val cluster_tools         = "com.typesafe.akka"       %%    "akka-cluster-tools"            %  Akka withSources()

    val persistence           = "com.typesafe.akka"       %%    "akka-persistence"              % Akka withSources() intransitive()
    val persistence_cassandra = "com.github.krasserm"     %%    "akka-persistence-cassandra"    % "0.4"

    val akka_distributed_data = "com.typesafe.akka"       %%    "akka-distributed-data-experimental" % Akka
    
    object streams {
      val akka_http                = "com.typesafe.akka"    %%    "akka-http-experimental"        % AkkaStreamsVersion withSources()
      val akka_http_core           = "com.typesafe.akka"    %%    "akka-http-core-experimental"   % AkkaStreamsVersion withSources()
    }

    val slf4j                 = "com.typesafe.akka"       %%    "akka-slf4j"                    % Akka
    val testkit               = "com.typesafe.akka"       %%    "akka-testkit"                  % Akka
    val multi_node_testkit    = "com.typesafe.akka"       %%    "akka-multi-node-testkit"       % Akka
  }

  val json4s = "org.json4s"             %%    "json4s-native"   % "3.2.10"

  val spray_json = "io.spray"           %%    "spray-json"      % "1.2.6" withSources()

  val jsoup = "org.jsoup"               %     "jsoup"           % "1.8.2"

  val joda_time = "joda-time"           %     "joda-time"       % "2.9"

  val nscala_time  = "com.github.nscala-time" %% "nscala-time"   % "1.6.0"

  val slf4j_api        = "org.slf4j"        %     "slf4j-api"       % Akka
  val logback          = "ch.qos.logback"   %     "logback-classic" % "1.1.2"

  val specs2           = "org.specs2"             %% "specs2"                % "3.0-M1"   %   "test"
  val scalatest        = "org.scalatest"          %% "scalatest"             % "2.2.5"    %   "test"
  val scalacheck       = "org.scalacheck"         %% "scalacheck"            % "1.12.4"   %   "test" exclude("org.scala-lang", "*")

  //val embedMongo       = "de.flapdoodle.embed"    %  "de.flapdoodle.embed.mongo"  % "1.43"    % "test"

  val typesafe_config  = "com.typesafe"           %  "config"                % "1.2.1"

  val scalaz           = "org.scalaz"             %% "scalaz-core"            % "7.1.4"  withSources()

  val guava            = "com.google.guava"       % "guava"                   % "18.0" withSources()

  val hystrix          = "com.netflix.hystrix"    %  "hystrix-core"           % Hystrix

  val hystrix_stream   = "com.netflix.hystrix"    %  "hystrix-metrics-event-stream" % Hystrix
  
  val turbine          = "com.netflix.turbine"    %  "turbine-core"          % "2.0.0-DP.2"

  val rxscala          = "io.reactivex"           %% "rxscala"                % "0.25.0"

  val protobuf         = "com.google.protobuf"    %  "protobuf-java"           % "2.5.0"

  val nosql_join       = "com.haghard"            %% "nosql-join-stream"       % "0.1.5-SNAPSHOT"

  val http_session     = "com.softwaremill"       %% "akka-http-session"       % "0.1.4"
}
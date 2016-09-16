import sbt._

object Dependencies {

  val Scala = "2.11.8"
  val crossScala = Seq(Scala, "2.10.5")

  val Akka = "2.4.9"
  val Hystrix = "1.4.14"

  implicit class Exclude(module: ModuleID) {
    def akkaExclude: ModuleID = module
      .excludeAll(ExclusionRule("com.typesafe"))
      .excludeAll(ExclusionRule("org.slf4j"))
  }

  object akka {
    val actor                 = "com.typesafe.akka"       %%    "akka-actor"                    % Akka withSources() //akkaExclude
    val cluster               = "com.typesafe.akka"       %%    "akka-cluster"                  % Akka withSources()

    val sharding              = "com.typesafe.akka"       %%    "akka-cluster-sharding"         %  Akka withSources()
    val cluster_tools         = "com.typesafe.akka"       %%    "akka-cluster-tools"            %  Akka withSources()

    val persistence           = "com.typesafe.akka"       %%    "akka-persistence"              % Akka withSources() intransitive()
    val persistence_cassandra = "com.typesafe.akka"       %%    "akka-persistence-cassandra"    % "0.17"

    val akka_distributed_data = "com.typesafe.akka"       %%    "akka-distributed-data-experimental" % Akka


    val httpCore              = "com.typesafe.akka"       %%    "akka-http-core"                % Akka
    val http                  = "com.typesafe.akka"       %%    "akka-http-experimental"        % Akka

    val slf4j                 = "com.typesafe.akka"       %%    "akka-slf4j"                    % Akka
    val testkit               = "com.typesafe.akka"       %%    "akka-testkit"                  % Akka
    val multi_node_testkit    = "com.typesafe.akka"       %%    "akka-multi-node-testkit"       % Akka
  }

  val json4s = "org.json4s"             %%    "json4s-native"   % "3.2.10"

  val spray_json = "io.spray"           %%    "spray-json"      % "1.2.6" withSources()

  val jsoup = "org.jsoup"               %     "jsoup"           % "1.8.2"

  val joda_time = "joda-time"           %     "joda-time"       % "2.9"

  val nscala_time  = "com.github.nscala-time" %% "nscala-time"   % "1.6.0"

  val specs2           = "org.specs2"             %% "specs2"                % "3.0-M1"   %   "test"
  val scalatest        = "org.scalatest"          %% "scalatest"             % "2.2.5"    %   "test"
  val scalacheck       = "org.scalacheck"         %% "scalacheck"            % "1.12.4"   %   "test" exclude("org.scala-lang", "*")

  val typesafe_config  = "com.typesafe"           %  "config"                % "1.2.1"

  val scalaz           = "org.scalaz"             %% "scalaz-core"            % "7.1.4"  withSources()

  val guava            = "com.google.guava"       % "guava"                   % "18.0" withSources()

  val hystrix          = "com.netflix.hystrix"    %  "hystrix-core"           % Hystrix

  val hystrix_stream   = "com.netflix.hystrix"    %  "hystrix-metrics-event-stream" % Hystrix
  
  val turbine          = "com.netflix.turbine"    %  "turbine-core"          % "2.0.0-DP.2"

  val rxscala          = "io.reactivex"           %% "rxscala"                % "0.25.0"

  val protobuf         = "com.google.protobuf"    %  "protobuf-java"           % "2.5.0"

  val nosql_join       = "com.haghard"            %% "nosql-join-stream"       % "0.2.3"

  val http_session     = "com.softwaremill.akka-http-session"  %%  "core"      % "0.2.7"

  val jbcrypt          = "org.mindrot"             %  "jbcrypt"                % "0.3m"
}
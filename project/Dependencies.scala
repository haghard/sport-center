import sbt._

object Dependencies {

  val Scala = "2.11.5"
  val Akka = "2.3.9"
  val AkkaDataReplication = "0.9"
  val AkkaStreamsVersion = "1.0-M2"

  object akka {
    val actor                 = "com.typesafe.akka"       %%    "akka-actor"                    % Akka withSources()
    val cluster               = "com.typesafe.akka"       %%    "akka-cluster"                  % Akka withSources()
    val contrib               = "com.typesafe.akka"       %%    "akka-contrib"                  % Akka intransitive()

    //val persistence_cassandra = "com.github.krasserm" %% "akka-persistence-cassandra"    % "0.3.4" intransitive()
    val persistence           = "com.typesafe.akka"       %%    "akka-persistence-experimental" % Akka withSources() intransitive()
    val persistence_mongo     = "com.github.ironfish"     %%    "akka-persistence-mongo-casbah" % "0.7.6" withSources()

    val akka_data_replication = "com.github.patriknw"     %%    "akka-data-replication"         % AkkaDataReplication intransitive()

    object streams {
      val streamz_akka_persistence = "com.github.krasserm"  %%    "streamz-akka-persistence"    % "0.2"              withSources()
      val akka_streams             = "com.typesafe.akka"    %%    "akka-stream-experimental"    % AkkaStreamsVersion withSources()
      val akka_http                = "com.typesafe.akka"    %%    "akka-http-experimental"      % AkkaStreamsVersion withSources()
      val akka_http_core           = "com.typesafe.akka"    %%    "akka-http-core-experimental" % AkkaStreamsVersion withSources()
    }

    val slf4j                 = "com.typesafe.akka"       %%    "akka-slf4j"                    % Akka
    val testkit               = "com.typesafe.akka"       %%    "akka-testkit"                  % Akka
  }

  val json4s = "org.json4s"             %%    "json4s-native"   % "3.2.10"

  val spray_json = "io.spray"           %%    "spray-json"        % "1.2.6" withSources()

  val jsoup = "org.jsoup"               %     "jsoup"           % "1.7.3"

  val joda_time = "joda-time"           %     "joda-time"       % "2.5"

  val nscala_time = "com.github.nscala-time" %% "nscala-time"   % "1.6.0"

  val logback_core = "ch.qos.logback"   %     "logback-core"    % "1.1.2"
  val logback      = "ch.qos.logback"   %     "logback-classic" % "1.1.2"
  val slf4j_api    = "org.slf4j"        %     "slf4j-api"       % "1.7.7"

  val scalacompiler    = "org.scala-lang"         %  "scala-compiler"        % Scala

  val specs2           = "org.specs2"             %% "specs2"                % "3.0-M1"
  val scalatest        = "org.scalatest"          %% "scalatest"             % "2.2.1"
  val scalacheck       = "org.scalacheck"         %% "scalacheck"            % "1.11.6" % "test" exclude("org.scala-lang", "*")

  val typesafe_config  = "com.typesafe"           %  "config"                % "1.2.1"

  val scalaz           = "org.scalaz"             %% "scalaz-core"            % "7.1.0" withSources()

  val guava            = "com.google.guava"       % "guava"                   % "18.0" withSources()
}
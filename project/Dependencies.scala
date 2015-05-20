import sbt._

object Dependencies {

  val crossScala = Seq("2.11.6", "2.10.5")
  val Scala = "2.11.6"

  val Akka = "2.3.11"
  val AkkaDataReplication = "0.11"
  val AkkaStreamsVersion = "1.0-RC2"
  val Hystrix = "1.4.0"

  object akka {
    val actor                 = "com.typesafe.akka"       %%    "akka-actor"                    % Akka withSources()
    val cluster               = "com.typesafe.akka"       %%    "akka-cluster"                  % Akka withSources()
    val contrib               = "com.typesafe.akka"       %%    "akka-contrib"                  % Akka intransitive()

    val persistence           = "com.typesafe.akka"       %%    "akka-persistence-experimental" % Akka withSources() intransitive()

    val persistence_cassandra = "com.github.krasserm"     %%    "akka-persistence-cassandra"    % "0.3.7"

    val akka_data_replication = "com.github.patriknw"     %%    "akka-data-replication"         % AkkaDataReplication intransitive()
    
    object streams {
      val streamz_akka_persistence = "com.github.krasserm"  %%    "streamz-akka-persistence"      % "0.2"    withSources()
      val akka_streams             = "com.typesafe.akka"    %%    "akka-stream-experimental"      % AkkaStreamsVersion withSources()
      val akka_http                = "com.typesafe.akka"    %%    "akka-http-scala-experimental"  % AkkaStreamsVersion withSources()
      val akka_http_core           = "com.typesafe.akka"    %%    "akka-http-core-experimental"   % AkkaStreamsVersion withSources()
    }

    val slf4j                 = "com.typesafe.akka"       %%    "akka-slf4j"                    % Akka
    val testkit               = "com.typesafe.akka"       %%    "akka-testkit"                  % Akka
    val multi_node_testkit    = "com.typesafe.akka"       %%    "akka-multi-node-testkit"       % Akka
  }

  val sparkCassandra    = "com.datastax.spark"  %% "spark-cassandra-connector"          % "1.3.0-SNAPSHOT" //current build, works fine

  object spark {
    val Spark = "1.3.1"

    val core           = ("org.apache.spark"        %% "spark-core"            % Spark).exclude("com.typesafe.akka", "akka-actor")
    //val sparkStreaming = "org.apache.spark"        %% "spark-streaming"       % Spark
    //val sparkSql       = "org.apache.spark"        %% "spark-sql"             % Spark
    //val mllib = ("org.apache.spark" %% "spark-mllib" % version).exclude("org.slf4j", "slf4j-api")
  }

  val json4s = "org.json4s"             %%    "json4s-native"   % "3.2.10"

  val spray_json = "io.spray"           %%    "spray-json"      % "1.2.6" withSources()

  val jsoup = "org.jsoup"               %     "jsoup"           % "1.7.3"

  val joda_time = "joda-time"           %     "joda-time"       % "2.5"

  val nscala_time = "com.github.nscala-time" %% "nscala-time"   % "1.6.0"

  val logback_core = "ch.qos.logback"   %     "logback-core"    % "1.1.2"
  val logback      = "ch.qos.logback"   %     "logback-classic" % "1.1.2"
  val slf4j_api    = "org.slf4j"        %     "slf4j-api"       % "1.7.7"

  //val scalacompiler    = "org.scala-lang"         %  "scala-compiler"        % Scala

  val specs2           = "org.specs2"             %% "specs2"                % "3.0-M1"   %   "test"
  val scalatest        = "org.scalatest"          %% "scalatest"             % "2.2.0"    %   "test"
  val scalacheck       = "org.scalacheck"         %% "scalacheck"            % "1.11.6"   %   "test" exclude("org.scala-lang", "*")

  val embedMongo       = "de.flapdoodle.embed"    %  "de.flapdoodle.embed.mongo"  % "1.43"    % "test"

  val typesafe_config  = "com.typesafe"           %  "config"                % "1.2.1"

  val scalaz           = "org.scalaz"             %% "scalaz-core"            % "7.1.0" withSources()

  val guava            = "com.google.guava"       % "guava"                   % "18.0" withSources()

  val hystrix          = "com.netflix.hystrix"    %  "hystrix-core"           % Hystrix

  val hystrix_stream   = "com.netflix.hystrix"    %  "hystrix-metrics-event-stream" % Hystrix
  
  val turbine          = "com.netflix.turbine"    %  "turbine-core"          % "2.0.0-DP.2"

  val rxscala          = "io.reactivex"           %% "rxscala"                % "0.23.1"

  val protobuf         = "com.google.protobuf"    %  "protobuf-java"           % "2.5.0"

  //val spark            =  "org.apache.spark"      %% "spark-core"              % "1.3.1"
  //val spark_streaming  =  "org.apache.spark"      %% "spark-streaming"         % "1.3.1"
}
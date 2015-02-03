import sbt._
import Dependencies._
import sbtassembly.Plugin.AssemblyKeys._

name := "microservices"

scalaVersion := Scala

libraryDependencies ++= Seq(
  akka.actor,
  akka.cluster,
  akka.contrib,
  //
  akka.persistence,
  akka.persistence_mongo,
  akka.akka_data_replication,
  //
  akka.streams.akka_streams,
  akka.streams.streamz_akka_persistence,
  //
  akka.slf4j,
  //TEST
  akka.testkit % "test",
  //akka.multi_node_testkit % "test",
  //json
  spray_json,
  //TEST
  //spray.testkit,
  //OTHER
  guava,
  jsoup,
  joda_time,
  logback_core,
  logback,
  slf4j_api,
  json4s,
  scalacompiler,
  typesafe_config,
  specs2,
  scalatest,
  scalacheck
)

val clusterNodeType = settingKey[String]("Type of node that we gonna build")
val startClass = settingKey[String]("Main class to run")

//set nodeType := "crawler"
//set nodeType := "tweeter-feed"
clusterNodeType := "backend"

//set startClass := "crawl.CloudCrawler"
//set startClass := "twitter.CloudTwitterFeed"
startClass := "backend.CloudBackend"

//https://github.com/sbt/sbt-assembly
assemblySettings

assemblyOption in packageDependency ~= { _.copy(appendContentHash = true) }

test in assembly := {}

jarName in assembly := "scenter-" + clusterNodeType.value + ".jar"

mainClass in assembly := Some(startClass.value)

mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
  {
    case "META-INF/MANIFEST.MF" => MergeStrategy.discard
    case "logback.xml"       => MergeStrategy.first
    case x => old(x)
  }
}

excludedJars in assembly <<= (fullClasspath in assembly) map { cp =>
  cp filter {_.data.getName == "mockito-core-1.9.5.jar"}
}

//show discoveredMainClasses
//assembly
//microservices/run
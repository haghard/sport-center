import Dependencies._

import scalariform.formatter.preferences._

name := "ddd-core"

scalaVersion := Scala

//scalariformSettings
//ScalariformKeys.preferences =: formattingPreferences


libraryDependencies ++= Seq(
  akka.actor,
  akka.cluster,
  akka.cluster_tools,
  akka.sharding,
  akka.persistence_cassandra,
  akka.slf4j,
  //TEST
  akka.testkit % "test",
  scalatest)

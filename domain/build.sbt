import Dependencies._

import scalariform.formatter.preferences._

name := "domain"

scalaVersion := Scala

parallelExecution in Test := false

net.virtualvoid.sbt.graph.Plugin.graphSettings
//domain/dependency-Tree

libraryDependencies ++= Seq(
  protobuf,
  akka.actor,
  akka.cluster,
  //
  akka.persistence_cassandra,
  akka.slf4j,
  nosql_join,
  //TEST
  akka.testkit % "test",
  scalatest)
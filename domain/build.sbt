import Dependencies._

import scalariform.formatter.preferences._

name := "domain"

scalaVersion := Scala

parallelExecution in Test := false

scalariformSettings

ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(PreserveDanglingCloseParenthesis, true)

net.virtualvoid.sbt.graph.Plugin.graphSettings
//domain/dependency-Tree


libraryDependencies ++= Seq(
  protobuf,
  akka.actor,
  akka.cluster,
  //
  akka.persistence_cassandra,
  akka.slf4j,
  //akka.streams.streamz_akka_persistence,
  //scalaz_stream,
  nosql_join,
  //TEST
  akka.testkit % "test",
  scalatest,
  embedMongo)
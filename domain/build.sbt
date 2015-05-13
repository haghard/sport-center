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

libraryDependencies ++= Seq(
  protobuf,
  akka.actor,
  akka.cluster,
  akka.contrib,
  //
  akka.persistence,
  akka.persistence_cassandra,
  akka.slf4j,
  akka.streams.streamz_akka_persistence,
  //akka.akka_ddd_core,
  //TEST
  akka.testkit % "test",
  scalatest,
  embedMongo)
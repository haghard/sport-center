import Dependencies._

import scalariform.formatter.preferences._

name := "ddd-core"

scalaVersion := Scala

scalariformSettings

ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(PreserveDanglingCloseParenthesis, true)


libraryDependencies ++= Seq(
  akka.actor,
  akka.cluster,
  akka.contrib,
  //
  akka.persistence,
  akka.persistence_mongo,
  akka.slf4j,
  akka.streams.streamz_akka_persistence,
  //akka.akka_ddd_core,
  //TEST
  akka.testkit % "test",
  scalatest,
  embedMongo)

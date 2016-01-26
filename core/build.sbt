import sbt._
import Dependencies._
import scalariform.formatter.preferences._

name := "core"

scalaVersion := Scala

scalariformSettings

ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(PreserveDanglingCloseParenthesis, true)

libraryDependencies ++= Seq(
  akka.cluster,
  spray_json,
  json4s,
  scalaz,
  nscala_time,
  guava,
  protobuf,
  http_session)

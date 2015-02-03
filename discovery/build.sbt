import sbt._
import Dependencies._
import sbtassembly.Plugin.AssemblyKeys._
import scalariform.formatter.preferences._
import scalariform.formatter.preferences._

name := "discovery"

scalaVersion := Scala

scalariformSettings

ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(PreserveDanglingCloseParenthesis, true)

libraryDependencies ++= Seq(
  akka.cluster,
  akka.contrib,
  akka.akka_data_replication,
  akka.streams.akka_http)
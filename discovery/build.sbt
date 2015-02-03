import sbt._
import Dependencies._
import sbtassembly.Plugin.AssemblyKeys._
import scalariform.formatter.preferences._

name := "discovery"

scalaVersion := Scala


libraryDependencies ++= Seq(
  akka.cluster,
  akka.contrib,
  akka.akka_data_replication,
  akka.streams.akka_http)
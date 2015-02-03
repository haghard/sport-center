import sbt._
import Dependencies._
import sbtassembly.Plugin.AssemblyKeys._

name := "bootstrap"

scalaVersion := Scala

libraryDependencies ++= Seq(
  akka.cluster,
  akka.streams.akka_http,
  spray_json,
  json4s,
  scalaz,
  nscala_time,
  guava)

import sbt._
import Dependencies._

name := "core"

scalaVersion := Scala


libraryDependencies ++= Seq(
  akka.cluster,
  spray_json,
  json4s,
  scalaz,
  nscala_time,
  guava,
  protobuf,
  http_session,
  akka.http,
  akka.httpCore)

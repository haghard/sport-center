import Dependencies._

import scalariform.formatter.preferences._

name := "domain"

scalaVersion := Scala

libraryDependencies ++= Seq(
  akka.actor,
  akka.cluster,
  akka.contrib,
  //
  akka.persistence,
  akka.persistence_mongo,
  akka.slf4j,
  akka.streams.streamz_akka_persistence,
  //TEST
  akka.testkit % "test")
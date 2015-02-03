import Dependencies._

import scalariform.formatter.preferences.{AlignSingleLineCaseStatements, AlignParameters, RewriteArrowSymbols}

name := "crawler-microservice"

scalaVersion := Scala

libraryDependencies ++= Seq(
  jsoup,
  akka.testkit,
  scalatest
)
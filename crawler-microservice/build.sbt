import Dependencies._
import scalariform.formatter.preferences._

name := "crawler-microservice"

scalaVersion := Scala

libraryDependencies ++= Seq(
  jsoup,
  akka.testkit,
  scalatest,
  http_session,
  akka.slf4j
)
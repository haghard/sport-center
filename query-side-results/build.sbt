import Dependencies._
import scalariform.formatter.preferences._

name := "query-side-results"

scalaVersion := Scala

libraryDependencies ++= Seq(hystrix, nosql_join, akka.persistence, nosql_join, http_session)

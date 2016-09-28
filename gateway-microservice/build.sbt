import Dependencies._
import scalariform.formatter.preferences._

name := "gateway-microservice"

scalaVersion := Scala

net.virtualvoid.sbt.graph.Plugin.graphSettings
//gatewayMicroservices/dependency-graph

libraryDependencies ++= Seq(
  hystrix,
  hystrix_stream,
  hystrix_codahale,
  metrics_graphite,
  turbine,
  rxscala,
  akka.http,
  akka.httpCore,
  jbcrypt
)

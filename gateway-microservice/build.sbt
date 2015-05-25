import Dependencies._
import scalariform.formatter.preferences._

name := "gateway-microservice"

scalaVersion := Scala

scalariformSettings

ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(PreserveDanglingCloseParenthesis, true)

net.virtualvoid.sbt.graph.Plugin.graphSettings
//gatewayMicroservices/dependency-graph

libraryDependencies ++= Seq(
  hystrix,
  hystrix_stream,
  turbine,
  rxscala
)

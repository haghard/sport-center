import Dependencies._
import scalariform.formatter.preferences._

name := "analytics-microservice"

scalaVersion := Scala

scalariformSettings

ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(PreserveDanglingCloseParenthesis, true)

net.virtualvoid.sbt.graph.Plugin.graphSettings

//analyticsMicroservice/dependency-graph

libraryDependencies ++= Seq(sparkCassandra, spark.core/*, spark.sparkStreaming, spark.sparkSql*/)
import Dependencies._
import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

name := "discovery"

scalaVersion := Scala

scalariformSettings

net.virtualvoid.sbt.graph.Plugin.graphSettings

ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(PreserveDanglingCloseParenthesis, true)

libraryDependencies ++= Seq(
  akka.cluster,
  akka.contrib,
  akka.akka_data_replication,
  akka.streams.akka_http,
  akka.multi_node_testkit,
  scalatest
)

Project.settings ++ SbtMultiJvm.multiJvmSettings ++ Seq(
  compile in MultiJvm <<= (compile in MultiJvm) triggeredBy (compile in Test),

  executeTests in Test <<= (executeTests in Test, executeTests in MultiJvm) map {
    case (testResults, multiNodeResults)  =>
      val overall =
        if (testResults.overall.id < multiNodeResults.overall.id)
          multiNodeResults.overall
        else
          testResults.overall
      Tests.Output(overall,
        testResults.events ++ multiNodeResults.events,
        testResults.summaries ++ multiNodeResults.summaries)
  }
)
import Dependencies._
import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

name := "discovery"

scalaVersion := Scala


net.virtualvoid.sbt.graph.Plugin.graphSettings
//discovery/dependency-graph

libraryDependencies ++= Seq(
  akka.cluster,
  akka.cluster_tools,
  akka.akka_distributed_data,
  akka.multi_node_testkit,
  scalatest
)

/*
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
)*/

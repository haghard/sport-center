import Dependencies._
import sbt._
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import com.typesafe.sbt.SbtScalariform._
import sbtdocker.ImageName

//import sbtassembly.Plugin.AssemblyKeys._
//import sbtassembly.Plugin.MergeStrategy

import scalariform.formatter.preferences._

organization := "github.com"

scalariformSettings

ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(PreserveDanglingCloseParenthesis, true)

name := "bootstrap"

scalaVersion := Scala

enablePlugins(DockerPlugin)


val clusterNodeType = settingKey[String]("Type of node that we gonna build")
clusterNodeType := "local-gateway"

val mainJarClass = settingKey[String]("Main class to run")
mainJarClass := "configuration.local.LocalRouter"

assemblyJarName in assembly := "scenter-" + clusterNodeType.value + ".jar"

assemblyMergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
  {
    case "META-INF/MANIFEST.MF" => MergeStrategy.discard
    case "META-INF/io.netty.versions.properties" => MergeStrategy.discard
    case "*.properties"     => MergeStrategy.discard
    case "logback.xml"       => MergeStrategy.first
    case x => old(x)
  }
}

mainClass in assembly := Some(mainJarClass.value)

docker <<= (docker dependsOn sbtassembly.AssemblyKeys.assembly)

dockerfile in docker := {
  val jarFile = (assemblyOutputPath in assembly).value
  val appDirPath = "/services"
  val jarTargetPath = s"$appDirPath/${jarFile.name}"

  new Dockerfile {
    from("dockerfile/java:oracle-java8")
    add(jarFile, jarTargetPath)
    workDir(appDirPath)
    runRaw("ifconfig")
    //expose(2551, 2561)
    entryPoint("java", "-jar", jarTargetPath, "2551", "HTTP_PORT=2561")
    //entryPoint("sh", "-c", "CLUSTER_IP=`/sbin/ifconfig eth0 | grep 'inet addr:' | cut -d: -f2 | awk '{ print $1 }'` bin/clustering $*")
    //entryPoint("sh", "-c", "export=HOST_IP0=$(ifconfig eth0 | grep 'inet addr:' | cut -d: -f2 | awk '{ print $1 }')")
    maintainer("Haghard")
    env("MONGO_HOST" -> "192.168.0.62",  "MONGO_PORT" -> "27017")
  }
}

imageNames in docker := Seq(
  ImageName(namespace = Some("sport-center"), repository = "gateway", tag = Some("v0.1")))
  //ImageName("sport-center/gateway/v0.1"))
//ImageName(namespace = Some("sport-center"), repository = "gateway", tag = Some("v0.1"))

buildOptions in docker := BuildOptions(cache = false,
  removeIntermediateContainers = BuildOptions.Remove.Always,
  pullBaseImage = BuildOptions.Pull.Always)


addCommandAlias("lgateway0", "bootstrap/run-main configuration.local.LocalRouter 2551 -Dhttp.port=2561")
addCommandAlias("lgateway1", "bootstrap/run-main configuration.local.LocalRouter 2552 -Dhttp.port=2562")
addCommandAlias("lgateway2", "bootstrap/run-main configuration.local.LocalRouter 2553 -Dhttp.port=2563")


addCommandAlias("lresults0", "bootstrap/run-main configuration.local.LocalResultsQuerySide 2555 -Dhttp.port=9001")
addCommandAlias("lresults1", "bootstrap/run-main configuration.local.LocalResultsQuerySide 2556 -Dhttp.port=9002")
addCommandAlias("lresults2", "bootstrap/run-main configuration.local.LocalResultsQuerySide 2557 -Dhttp.port=9003")


addCommandAlias("lstandings1", "bootstrap/run-main configuration.local.LocalStandingSide 3561 -Dhttp.port=9011")
addCommandAlias("lstandings2", "bootstrap/run-main configuration.local.LocalStandingSide 3562 -Dhttp.port=9010")


addCommandAlias("lcrawler0", "bootstrap/run-main configuration.local.LocalCrawler 3558 -Dhttp.port=9007")
addCommandAlias("lcrawler1", "bootstrap/run-main configuration.local.LocalCrawler 3559 -Dhttp.port=9008")
addCommandAlias("lcrawler2", "bootstrap/run-main configuration.local.LocalCrawler 3560 -Dhttp.port=9009")

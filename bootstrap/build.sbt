import Dependencies._
import sbt._
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import com.typesafe.sbt.SbtScalariform._
import sbtdocker.ImageName
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

val mainJarClass = settingKey[String]("Main class to run")

val clusterNodeType = settingKey[String]("Type of node that we gonna build")

//clusterNodeType := "gateway-microservice"
//clusterNodeType := "query-side-results"
//clusterNodeType := "query-side-standings"
clusterNodeType := "crawler-microservice"

//mainJarClass := "configuration.QueryResultsSideBootstrap"
//mainJarClass := "configuration.QueryStandingSideBootstrap"
//mainJarClass := "configuration.GatewayBootstrap"
mainJarClass := "configuration.CrawlerBootstrap"

assemblyJarName in assembly := s"scenter-${clusterNodeType.value}.jar"

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
  val appDirPath = "/sport-center"
  val jarTargetPath = s"$appDirPath/${jarFile.name}"
  val settingPath = s"$appDirPath/settings"

  new Dockerfile {
    from("dockerfile/java:oracle-java8")

    add(jarFile, jarTargetPath)

    runRaw(s"mkdir $settingPath")

    add(new File(s"${clusterNodeType.value}/settings/${clusterNodeType.value}-archaius.properties"),
        s"${appDirPath}/settings/${clusterNodeType.value}-archaius.properties")

    workDir(appDirPath)
    runRaw("ifconfig")

    //expose(2551, 2561)
    //entryPoint("sh", "-c", "export=HOST_IP0=$(ifconfig eth0 | grep 'inet addr:' | cut -d: -f2 | awk '{ print $1 }')")
    maintainer("Haghard")
    env("MONGO_HOST" -> "192.168.0.62",  "MONGO_PORT" -> "27017",
        "archaius.configurationSource.additionalUrls" -> s"sport-center/${appDirPath}/settings/${clusterNodeType.value}-archaius.properties")
    entryPoint("java", "-jar", jarTargetPath, "-Xmx1256m", "-XX:MaxPermSize=512m")
  }
}

imageNames in docker := Seq(
  ImageName(namespace = Some("haghard"),
    repository = "sport-center-" + clusterNodeType.value, tag = Some("v0.1")))

buildOptions in docker := BuildOptions(
  cache = false,
  removeIntermediateContainers = BuildOptions.Remove.Always,
  pullBaseImage = BuildOptions.Pull.Always)

//https://github.com/marcuslonnberg/sbt-docker
//https://groups.google.com/forum/#!topic/akka-user/PaNIPdyD4ck
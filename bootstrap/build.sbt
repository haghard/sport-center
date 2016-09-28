import Dependencies._
import sbt._
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import com.typesafe.sbt.SbtScalariform._
import sbtdocker.ImageName
import scalariform.formatter.preferences._

organization := "haghard"

name := "bootstrap"

scalaVersion := Scala

enablePlugins(_root_.sbtdocker.DockerPlugin)

val mainJarClass = settingKey[String]("Main class to run")

val clusterNodeType = settingKey[String]("Type of node that we gonna build")

clusterNodeType := "gateway-microservice"
//clusterNodeType := "crawler-microservice"
//clusterNodeType := "query-side-results"
//clusterNodeType := "query-side-standings"

mainJarClass := "configuration.container.GatewayBootstrap"
//mainJarClass := "configuration.container.CrawlerBootstrap"
//mainJarClass := "configuration.container.QueryResultsBootstrap"
//mainJarClass := "configuration.container.QueryStandingBootstrap"

assemblyJarName in assembly := s"scenter-${clusterNodeType.value}.jar"

assemblyMergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
  {
    case "META-INF/MANIFEST.MF" => MergeStrategy.discard
    case "META-INF/io.netty.versions.properties" => MergeStrategy.discard
    case "*.properties"     => MergeStrategy.discard
    case "logback.xml"      => MergeStrategy.first
    case  PathList(ps @ _*) if ps.last contains "Effect.class"     => MergeStrategy.first
    case  PathList(ps @ _*) if ps.last contains "Predicate.class"  => MergeStrategy.first
    case  PathList(ps @ _*) if ps.last startsWith "Function"   => MergeStrategy.first
    case  PathList(ps @ _*) if ps.last startsWith "Procedure" =>  MergeStrategy.first
    case x => old(x)
  }
}

//This is only for stream RC3 - https://groups.google.com/forum/#!topic/akka-user/3MQGojdIBcU

mainClass in assembly := Some(mainJarClass.value)

docker <<= (docker dependsOn sbtassembly.AssemblyKeys.assembly)

dockerfile in docker := {
  val jarFile = (assemblyOutputPath in assembly).value
  val appDirPath = "/sport-center"
  val jarTargetPath = s"$appDirPath/${jarFile.name}"
  val settingPath = s"$appDirPath/settings"

  new Dockerfile {
    from("java:8u45")
    add(jarFile, jarTargetPath)
    runRaw(s"mkdir $settingPath")

    add(new File(s"${clusterNodeType.value}/settings/${clusterNodeType.value}-archaius.properties"),
        s"${appDirPath}/settings/${clusterNodeType.value}-archaius.properties")

    workDir(appDirPath)
    //runRaw("ifconfig")
    runRaw("ls -la")

    //expose(2551, 2561)
    env("archaius.configurationSource.additionalUrls" -> s"sport-center/${appDirPath}/settings/${clusterNodeType.value}-archaius.properties")
    entryPoint("java", "-Xmx1256m", "-XX:MaxMetaspaceSize=512m", "-XX:+HeapDumpOnOutOfMemoryError", "-jar", jarTargetPath)
  }
}

imageNames in docker := Seq(
  ImageName(namespace = Some("haghard"),
    repository = "sport-center-" + clusterNodeType.value, tag = Some("v0.4")))

buildOptions in docker := BuildOptions(cache = false,
  removeIntermediateContainers = BuildOptions.Remove.Always,
  pullBaseImage = BuildOptions.Pull.Always)


val cassandra = "192.168.0.182"

addCommandAlias("lgateway0", "bootstrap/run-main configuration.local.GatewayBootstrap --AKKA_PORT=2551 --HTTP_PORT=2561 --HOST=192.168.0.62 --SEED_NODES=192.168.0.62:2551,192.168.0.62:2552")
addCommandAlias("lgateway1", "bootstrap/run-main configuration.local.GatewayBootstrap --AKKA_PORT=2552 --HTTP_PORT=2562 --HOST=192.168.0.62 --SEED_NODES=192.168.0.62:2551,192.168.0.62:2552")
addCommandAlias("lgateway2", "bootstrap/run-main configuration.local.GatewayBootstrap --AKKA_PORT=2550 --HTTP_PORT=2560 --HOST=192.168.0.62 --SEED_NODES=192.168.0.62:2551,192.168.0.62:2552")

addCommandAlias("lcrawler0", "bootstrap/run-main configuration.local.CrawlerBootstrap --AKKA_PORT=2553 --HTTP_PORT=9001 --HOST=192.168.0.62 --DB_HOSTS=" + cassandra + " --SEED_NODES=192.168.0.62:2551,192.168.0.62:2552")
addCommandAlias("lcrawler1", "bootstrap/run-main configuration.local.CrawlerBootstrap --AKKA_PORT=2554 --HTTP_PORT=9002 --HOST=192.168.0.62 --DB_HOSTS=" + cassandra + " --SEED_NODES=192.168.0.62:2551,192.168.0.62:2552")

addCommandAlias("lresults0", "bootstrap/run-main configuration.local.QueryResultsBootstrap --AKKA_PORT=2555 --HTTP_PORT=9010 --DB_HOSTS=" + cassandra + " --HOST=192.168.0.62 --SEED_NODES=192.168.0.62:2551,192.168.0.62:2552")
addCommandAlias("lresults1", "bootstrap/run-main configuration.local.QueryResultsBootstrap --AKKA_PORT=2556 --HTTP_PORT=9011 --DB_HOSTS=" + cassandra)

addCommandAlias("lstanding0", "bootstrap/run-main configuration.local.QueryStandingBootstrap --AKKA_PORT=2557 --HTTP_PORT=9012 --DB_HOSTS=" + cassandra)
addCommandAlias("lstanding1", "bootstrap/run-main configuration.local.QueryStandingBootstrap --AKKA_PORT=2558 --HTTP_PORT=9013 --DB_HOSTS=" + cassandra)


//bootstrap/docker
//https://github.com/marcuslonnberg/sbt-docker
//https://groups.google.com/forum/#!topic/akka-user/PaNIPdyD4ck

//docker run --net="host" -it 426576ed0cb6 --AKKA_PORT=2555 --HTTP_PORT=2565
//docker run --net="host" -it 55b4382fae57 --AKKA_PORT=2555 --HTTP_PORT=2565 --SEED_NODES=192.168.0.182:2555,192.168.0.182:2556 --DB_HOSTS=192.168.0.182
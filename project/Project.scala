import Dependencies._
import sbt.Keys._
import sbt._

object Project {

  val localMvnRepo = "~/.m2/repository"
  val ivy = "~/.ivy2/local/"
  //"/Volumes/Data/dev_build_tools/apache-maven-3.1.1/repository"

  val settings = Defaults.defaultConfigs ++ Seq(
    name := "scenter",
    organization := "com.github.haghard",
    version := "0.1",
    parallelExecution in Test := false,
    scalaVersion := Scala,
    crossScalaVersions := Seq("2.10.4", Scala),
    licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),
    resolvers ++= Seq(
      "Sonatype Snapshots Repo"  at "https://oss.sonatype.org/content/groups/public",
      "Sonatype OSS Snapshots"   at "https://oss.sonatype.org/content/repositories/snapshots",
      "Sonatype OSS Releases"    at "https://oss.sonatype.org/content/repositories/releases",
      "Scalaz Bintray Repo"      at "http://dl.bintray.com/scalaz/releases",
    "m2" at	"https://repo1.maven.org/maven2/",
      "spray repo"               at "http://repo.spray.io",
      "krasserm at bintray"      at "http://dl.bintray.com/krasserm/maven",
      "patriknw at bintray"      at "http://dl.bintray.com/patriknw/maven",
      "Local Maven Repository"   at "file:///" + localMvnRepo,
      "Local Ivy Repository"     at "file:///" + ivy),

    publishMavenStyle := true,
    publishTo := Some(Resolver.file("file",  new File(localMvnRepo))),
    ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) },

    scalacOptions ++= Seq(
      "-encoding", "UTF-8",
      "-target:jvm-1.8",
      "-deprecation",
      "-unchecked",
      "-Ywarn-dead-code",
      "-feature",
      "-language:implicitConversions",
      "-language:postfixOps"
    ),
    javacOptions ++= Seq(
      "-source", "1.6",
      "-target", "1.8",
      "-Xlint:unchecked",
      "-Xlint:deprecation"
    ),
    javaOptions ++= Seq(
      "-Djava.library.path=./sigar",  //not used
      "-Xms226m",
      "-Xmx756m"
    )
  )
}
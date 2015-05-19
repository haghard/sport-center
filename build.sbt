import sbt._
import com.scalapenos.sbt.prompt.SbtPrompt.autoImport._
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm
import com.typesafe.sbt.SbtScalariform._

name := "sport-center"
organization := "github.com"

fork in Test := false

fork in IntegrationTest := false

parallelExecution in Test := false

promptTheme := Scalapenos

scalariformSettings


ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(RewriteArrowSymbols, true)
  .setPreference(AlignParameters, true)
  .setPreference(AlignSingleLineCaseStatements, true)


net.virtualvoid.sbt.graph.Plugin.graphSettings

lazy val root = project.in(file("."))
  .aggregate(`discovery`, `core`, `bootstrap`, `domain`,
             `gatewayMicroservices`, `querySideResults`, `querySideStandings`, `crawlerMicroservices`, `analyticsMicroservice`)


lazy val `core` = project.in(file("core"))

lazy val `bootstrap` = project.in(file("bootstrap")).dependsOn(`gatewayMicroservices`, `querySideResults`, `querySideStandings`, `analyticsMicroservice`, `crawlerMicroservices`)

lazy val `discovery` = project.in(file("discovery")).dependsOn(`core`).configs(MultiJvm)

lazy val `domain` = project.in(file("domain")).dependsOn(`core`, `dddCore`)

lazy val `gatewayMicroservices` = project.in(file("gateway-microservice")).dependsOn(`discovery`)

lazy val `querySideResults` = project.in(file("query-side-results")).dependsOn(`core`, `domain`, `discovery`)

lazy val `querySideStandings` = project.in(file("query-side-standings")).dependsOn(`core`, `domain`, `discovery`)

lazy val `crawlerMicroservices` = project.in(file("crawler-microservice")).dependsOn(`core`, `domain`, `discovery`, `dddCore`)

lazy val `dddCore` = project.in(file("ddd-core")).dependsOn(`core`)

lazy val `analyticsMicroservice` = project.in(file("analytics-microservice")).dependsOn(`core`, `domain`, `discovery`)


//future work
//lazy val `seedsMicroservices` = project.in(file("cluster-seeds-discovery")).dependsOn(`core`)


/**
 *
 *  Project sbt commands
 *         nodes: lgateway0, lgateway2, lgateway3     http GET 192.168.0.143:2561/routes
 *         nodes: lresults0, lresults1, lresults2,    http GET 192.168.0.143:2561/api/results/2014-01-29 | http GET 192.168.0.143:2561/api/results/okc/last
 *         nodes: lstandings0 lstandings1 lstandings2 http GET 192.168.0.143:2561/api/standings/2015-01-28
 *         nodes: lcrawler0 lcrawler1 lcrawler2       http GET 192.168.0.143:2561/api/crawler
 */

 /**
  *
  * cd /Volumes/Data/Code/Netflix/Hystrix/
  * ./gradlew jettyRun
  * http://localhost:7979/hystrix-dashboard
  *
  * To connect hystrix-dashboard to gateway-turbine use http://localhost:6500/turbine.stream
  */

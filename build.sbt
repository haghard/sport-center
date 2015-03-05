import com.scalapenos.sbt.prompt.SbtPrompt.autoImport._
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import scalariform.formatter.preferences._
import sbt.Keys._
import sbt._

name := "Sport Center"

promptTheme := Scalapenos

scalariformSettings

ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(RewriteArrowSymbols, true)
  .setPreference(AlignParameters, true)
  .setPreference(AlignSingleLineCaseStatements, true)

net.virtualvoid.sbt.graph.Plugin.graphSettings

lazy val sportcenter = project.in(file("."))
  .aggregate(`discovery`, `core`, `bootstrap`, `domain`,
             `gatewayMicroservices`, `querySideResults`, `querySideStandings`, `crawlerMicroservices`)

lazy val `core` = project.in(file("core"))

lazy val `bootstrap` = project.in(file("bootstrap"))
  .dependsOn(`gatewayMicroservices`, `querySideResults`, `querySideStandings`, `crawlerMicroservices`)

lazy val `discovery` = project.in(file("discovery")).dependsOn(`core`)

lazy val `domain` = project.in(file("domain")).dependsOn(`core`)

lazy val `gatewayMicroservices` = project.in(file("gateway-microservice")).dependsOn(`discovery`)

lazy val `querySideResults` = project.in(file("query-side-results")).dependsOn(`core`, `domain`, `discovery`)

lazy val `querySideStandings` = project.in(file("query-side-standings")).dependsOn(`core`, `domain`, `discovery`)

lazy val `crawlerMicroservices` = project.in(file("crawler-microservice")).dependsOn(`core`, `domain`, `discovery`)

//should be deleted
//lazy val microservices = project.in(file("microservices")).dependsOn(discovery, domain)



/**
 *
 *  Project sbt commands
 *         nodes: lb1, lb2, lb3                       http GET 192.168.0.143:2561/routes
 *         nodes: lresults1, lresults2, lresults3,    http GET 192.168.0.143:2561/api/results/2014-01-29 | http GET 192.168.0.143:2561/api/results/okc/last
 *         nodes: lstandings1 lstandings2 lstandings3 http GET 192.168.0.143:2561/api/standings/2015-01-28
 *         nodes: lcrawler1 lcrawler2 lcrawler3       http GET 192.168.0.143:2561/api/crawler
 */

 /**
  *
  * cd /Volumes/Data/Code/Netflix/Hystrix/hystrix-dashboard
  * ../gradlew jettyRun
  * http://localhost:7979/hystrix-dashboard
  *
  * To connect hystrix-dashboard to gateway-turbine use http://localhost:6500/turbine.stream
  */

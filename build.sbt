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

lazy val bootstrap = project.in(file("bootstrap"))

lazy val examples = project.in(file("examples")).dependsOn(discoveryMicroservices, microservices, querySideResults, querySideStandings, crawlerMicroservices)

lazy val discovery = project.in(file("discovery")).dependsOn(bootstrap)

lazy val domain = project.in(file("domain")).dependsOn(bootstrap)

lazy val discoveryMicroservices = project.in(file("discovery-microservice")).dependsOn(discovery)

lazy val querySideResults = project.in(file("query-side-results")).dependsOn(bootstrap, domain, discovery)

lazy val querySideStandings = project.in(file("query-side-standings")).dependsOn(bootstrap, domain, discovery)

lazy val crawlerMicroservices = project.in(file("crawler-microservice")).dependsOn(bootstrap, domain, discovery)

//should be deleted
lazy val microservices = project.in(file("microservices")).dependsOn(discovery, domain)

lazy val sportcenter = project.in(file(".")).aggregate(microservices, discovery, bootstrap,
  examples, domain, discoveryMicroservices, querySideResults, querySideStandings, crawlerMicroservices)


/**
 *
 *  Project sbt commands
 *         nodes: lb1, lb2, lb3                           http GET 192.168.0.143:2561/routes
 *         nodes: lresults1, lresults2, lresults3,        http GET 192.168.0.143:2561/api/results/2014-01-29
 *         nodes: lstandings1 lstandings2 lstandings3     http GET 192.168.0.143:2561/api/standings/2015-01-28
 *         nodes: lcrawler1 lcrawler2 lcrawler3           http GET 192.168.0.143:2561/api/crawler
 */
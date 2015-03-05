import Dependencies._

import scalariform.formatter.preferences._

scalariformSettings

ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(PreserveDanglingCloseParenthesis, true)

name := "bootstrap"

scalaVersion := Scala


addCommandAlias("lb1", "bootstrap/run-main configuration.local.LocalLoadBalancer 2551 -Dhttp.port=2561")
addCommandAlias("lb2", "bootstrap/run-main configuration.local.LocalLoadBalancer 2552 -Dhttp.port=2562")
addCommandAlias("lb3", "bootstrap/run-main configuration.local.LocalLoadBalancer 2553 -Dhttp.port=2563")
addCommandAlias("lb4", "bootstrap/run-main configuration.local.LocalLoadBalancer 2554 -Dhttp.port=2564")


addCommandAlias("lresults1", "bootstrap/run-main configuration.local.LocalResultsQuerySide 2555 -Dhttp.port=9001")
addCommandAlias("lresults2", "bootstrap/run-main configuration.local.LocalResultsQuerySide 2556 -Dhttp.port=9002")
addCommandAlias("lresults3", "bootstrap/run-main configuration.local.LocalResultsQuerySide 2557 -Dhttp.port=9003")


addCommandAlias("lstandings1", "bootstrap/run-main configuration.local.LocalStandingSide 3561 -Dhttp.port=9011")
addCommandAlias("lstandings2", "bootstrap/run-main configuration.local.LocalStandingSide 3562 -Dhttp.port=9010")


addCommandAlias("lcrawler1", "bootstrap/run-main configuration.local.LocalCrawler 3558 -Dhttp.port=9007")
addCommandAlias("lcrawler2", "bootstrap/run-main configuration.local.LocalCrawler 3559 -Dhttp.port=9008")
addCommandAlias("lcrawler3", "bootstrap/run-main configuration.local.LocalCrawler 3560 -Dhttp.port=9009")
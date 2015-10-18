import Dependencies._
import scalariform.formatter.preferences._

name := "crawler-microservice"

scalaVersion := Scala

scalariformSettings

ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(PreserveDanglingCloseParenthesis, true)

libraryDependencies ++= Seq(
  jsoup,
  akka.testkit,
  scalatest,
  http_session
)
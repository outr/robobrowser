name := "robobrowser"
organization := "com.outr"
version := "1.0.2-SNAPSHOT"
scalaVersion := "2.13.5"
crossScalaVersions := Seq("2.13.5", "2.12.13")

publishTo in ThisBuild := sonatypePublishTo.value
sonatypeProfileName in ThisBuild := "com.outr"
publishMavenStyle in ThisBuild := true
licenses in ThisBuild := Seq("MIT" -> url("https://github.com/outr/robobrowser/blob/master/LICENSE"))
sonatypeProjectHosting in ThisBuild := Some(xerial.sbt.Sonatype.GitHubHosting("outr", "robobrowser", "matt@outr.com"))
homepage in ThisBuild := Some(url("https://github.com/outr/robobrowser"))
scmInfo in ThisBuild := Some(
  ScmInfo(
    url("https://github.com/outr/robobrowser"),
    "scm:git@github.com:outr/robobrowser.git"
  )
)
developers in ThisBuild := List(
  Developer(id="darkfrog", name="Matt Hicks", email="matt@matthicks.com", url=url("http://matthicks.com"))
)

libraryDependencies ++= Seq(
  "org.seleniumhq.selenium" % "selenium-java" % "3.141.59",
  "io.youi" %% "youi-client" % "0.14.0",
  "org.jsoup" % "jsoup" % "1.13.1",
  "org.scalatest" %% "scalatest" % "3.2.9" % "test"
)

fork := true
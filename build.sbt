name := "robobrowser"
organization := "com.outr"
version := "1.2.2-SNAPSHOT"
scalaVersion := "2.13.6"
crossScalaVersions := Seq("2.13.6")
scalacOptions += "-deprecation"

publishTo := sonatypePublishTo.value
sonatypeProfileName := "com.outr"
publishMavenStyle := true
licenses := Seq("MIT" -> url("https://github.com/outr/robobrowser/blob/master/LICENSE"))
sonatypeProjectHosting := Some(xerial.sbt.Sonatype.GitHubHosting("outr", "robobrowser", "matt@outr.com"))
homepage := Some(url("https://github.com/outr/robobrowser"))
scmInfo := Some(
  ScmInfo(
    url("https://github.com/outr/robobrowser"),
    "scm:git@github.com:outr/robobrowser.git"
  )
)
developers := List(
  Developer(id="darkfrog", name="Matt Hicks", email="matt@matthicks.com", url=url("http://matthicks.com"))
)

libraryDependencies ++= Seq(
  "com.outr" %% "scribe-slf4j" % "3.6.3",
  "io.youi" %% "youi-client" % "0.14.3",
  "org.jsoup" % "jsoup" % "1.14.3",
  "io.appium" % "java-client" % "7.5.1",
  "com.lihaoyi" %% "sourcecode" % "0.2.7",
  "org.scalatest" %% "scalatest" % "3.2.10" % "test"
)

fork := true
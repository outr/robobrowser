name := "robobrowser"
organization := "com.outr"
version := "1.5.0-SNAPSHOT6"
scalaVersion := "2.13.8"
crossScalaVersions := Seq("2.13.8")
scalacOptions += "-deprecation"

resolvers += "jitpack.io" at "https://jitpack.io"
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
  "com.outr" %% "scribe-slf4j" % "3.6.9",
  "io.youi" %% "youi-client" % "0.14.4",
  "org.jsoup" % "jsoup" % "1.14.3",
  "com.github.appium" % "java-client" % "8.0.0-beta2",
  "org.seleniumhq.selenium" % "selenium-api" % "4.1.1",
  "org.seleniumhq.selenium" % "selenium-chrome-driver" % "4.1.1",
  "org.seleniumhq.selenium" % "selenium-remote-driver" % "4.1.1",
  "org.seleniumhq.selenium" % "htmlunit-driver" % "3.56.0",
  "org.seleniumhq.selenium" % "selenium-support" % "4.1.1",
  "com.lihaoyi" %% "sourcecode" % "0.2.7",
  "com.fifesoft" % "rsyntaxtextarea" % "3.1.6",
  "org.scalatest" %% "scalatest" % "3.2.12" % "test"
)

fork := true
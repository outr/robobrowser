name := "robobrowser"
organization := "com.outr"
version := "1.5.0-SNAPSHOT6"
scalaVersion := "2.13.10"
crossScalaVersions := Seq("2.13.10")
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

val seleniumVersion = "4.5.2"

libraryDependencies ++= Seq(
  "com.outr" %% "scribe-slf4j" % "3.10.4",
  "com.outr" %% "spice-client" % "0.0.2-SNAPSHOT",
  "org.jsoup" % "jsoup" % "1.15.3",
  "io.appium" % "java-client" % "8.2.0",
  "org.seleniumhq.selenium" % "selenium-api" % seleniumVersion,
  "org.seleniumhq.selenium" % "selenium-chrome-driver" % seleniumVersion,
  "org.seleniumhq.selenium" % "selenium-firefox-driver" % seleniumVersion,
  "org.seleniumhq.selenium" % "selenium-remote-driver" % seleniumVersion,
  "org.seleniumhq.selenium" % "htmlunit-driver" % seleniumVersion,
  "org.seleniumhq.selenium" % "selenium-support" % seleniumVersion,
  "com.lihaoyi" %% "sourcecode" % "0.3.0",
  "com.fifesoft" % "rsyntaxtextarea" % "3.3.0",
  "org.scalatest" %% "scalatest" % "3.2.14" % "test"
)

fork := true
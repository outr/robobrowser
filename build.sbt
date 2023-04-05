name := "robobrowser"
organization := "com.outr"
version := "1.5.1"
scalaVersion := "2.13.10"
crossScalaVersions := Seq("2.13.10", "3.2.2")
scalacOptions += "-deprecation"

resolvers += "jitpack.io" at "https://jitpack.io"

sonatypeCredentialHost := "s01.oss.sonatype.org"
sonatypeRepository := "https://s01.oss.sonatype.org/service/local"
publishTo := sonatypePublishToBundle.value
sonatypeProfileName := "com.outr"
publishMavenStyle := true
licenses := Seq("MIT" -> url("https://github.com/outr/robobrowser/blob/master/LICENSE"))
sonatypeProjectHosting := Some(xerial.sbt.Sonatype.GitHubHosting("outr", "robobrowser", "matt@matthicks.com"))
homepage := Some(url("https://github.com/outr/robobrowser"))
scmInfo := Some(
  ScmInfo(
    url("https://github.com/outr/robobrowser"),
    "scm:git@github.com:outr/robobrowser.git"
  )
)
developers := List(
  Developer(id="darkfrog", name="Matt Hicks", email="matt@matthicks.com", url=url("https://matthicks.com"))
)

val seleniumVersion = "4.8.3"

libraryDependencies ++= Seq(
  "com.outr" %% "scribe-slf4j" % "3.11.1",
  "com.outr" %% "spice-client" % "0.0.22",
  "com.outr" %% "spice-server-undertow" % "0.0.22",
  "org.jsoup" % "jsoup" % "1.15.4",
  "io.appium" % "java-client" % "8.3.0",
  "org.seleniumhq.selenium" % "selenium-api" % seleniumVersion,
  "org.seleniumhq.selenium" % "selenium-chrome-driver" % seleniumVersion,
  "org.seleniumhq.selenium" % "selenium-firefox-driver" % seleniumVersion,
  "org.seleniumhq.selenium" % "selenium-remote-driver" % seleniumVersion,
  "org.seleniumhq.selenium" % "htmlunit-driver" % seleniumVersion,
  "org.seleniumhq.selenium" % "selenium-support" % seleniumVersion,
  "com.lihaoyi" %% "sourcecode" % "0.3.0",
  "com.fifesoft" % "rsyntaxtextarea" % "3.3.3",
  "org.scalatest" %% "scalatest" % "3.2.15" % "test"
)

fork := true
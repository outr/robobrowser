name := "robobrowser"
organization := "com.outr"
version := "1.6.1-SNAPSHOT"
scalaVersion := "2.13.11"
crossScalaVersions := Seq("2.13.11", "3.3.0")
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

val seleniumVersion = "4.10.0"

val spiceVersion = "0.1.10"

libraryDependencies ++= Seq(
  "com.outr" %% "scribe-slf4j" % "3.11.9",
  "com.outr" %% "spice-client-okhttp" % spiceVersion,
  "com.outr" %% "spice-server-undertow" % spiceVersion,
  "org.jsoup" % "jsoup" % "1.16.1",
  "io.appium" % "java-client" % "8.5.1",
  "org.seleniumhq.selenium" % "selenium-api" % seleniumVersion,
  "org.seleniumhq.selenium" % "selenium-chrome-driver" % seleniumVersion,
  "org.seleniumhq.selenium" % "selenium-firefox-driver" % seleniumVersion,
  "org.seleniumhq.selenium" % "selenium-remote-driver" % seleniumVersion,
  "org.seleniumhq.selenium" % "htmlunit-driver" % seleniumVersion,
  "org.seleniumhq.selenium" % "selenium-support" % seleniumVersion,
  "org.seleniumhq.selenium" % "selenium-devtools-v112" % seleniumVersion,
  "com.lihaoyi" %% "sourcecode" % "0.3.0",
  "com.fifesoft" % "rsyntaxtextarea" % "3.3.4",
  "org.scalatest" %% "scalatest" % "3.2.16" % "test"
)

fork := true
outputStrategy := Some(StdoutOutput)
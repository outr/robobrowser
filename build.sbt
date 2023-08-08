name := "robobrowser"
organization := "com.outr"
version := "1.7.0-SNAPSHOT"

val scala213: String = "2.13.11"

val scala3: String = "3.3.0"

scalaVersion := scala213
crossScalaVersions := Seq(scala213, scala3)
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

val scribeVersion: String = "3.11.9"

val seleniumVersion: String = "4.10.0"

val spiceVersion: String = "0.1.10"

val jsoupVersion: String = "1.16.1"

val appiumVersion: String = "8.5.1"

val sourcecodeVersion: String = "0.3.0"

val rsyntaxtextareaVersion: String = "3.3.4"

val scalatestVersion: String = "3.2.16"

libraryDependencies ++= Seq(
  "com.outr" %% "scribe-slf4j2" % scribeVersion,
  "com.outr" %% "spice-client-okhttp" % spiceVersion,
  "com.outr" %% "spice-server-undertow" % spiceVersion,
  "org.jsoup" % "jsoup" % jsoupVersion,
  "io.appium" % "java-client" % appiumVersion,
  "org.seleniumhq.selenium" % "selenium-api" % seleniumVersion,
  "org.seleniumhq.selenium" % "selenium-chrome-driver" % seleniumVersion,
  "org.seleniumhq.selenium" % "selenium-firefox-driver" % seleniumVersion,
  "org.seleniumhq.selenium" % "selenium-remote-driver" % seleniumVersion,
  "org.seleniumhq.selenium" % "htmlunit-driver" % seleniumVersion,
  "org.seleniumhq.selenium" % "selenium-support" % seleniumVersion,
  "org.seleniumhq.selenium" % "selenium-devtools-v112" % seleniumVersion,
  "com.lihaoyi" %% "sourcecode" % sourcecodeVersion,
  "com.fifesoft" % "rsyntaxtextarea" % rsyntaxtextareaVersion,
  "org.scalatest" %% "scalatest" % scalatestVersion % "test"
)

fork := true
outputStrategy := Some(StdoutOutput)
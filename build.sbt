name := "robobrowser"
organization := "com.outr"
version := "1.7.3"

val scala213: String = "2.13.15"

val scala3: String = "3.3.4"

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

val scribeVersion: String = "3.15.2"

val seleniumVersion: String = "4.27.0"

val spiceVersion: String = "0.6.1"

val jsoupVersion: String = "1.18.2"

val appiumVersion: String = "9.3.0"

val sourcecodeVersion: String = "0.4.2"

val rsyntaxtextareaVersion: String = "3.5.2"

val scalatestVersion: String = "3.2.19"

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
  "org.seleniumhq.selenium" % "htmlunit-driver" % "4.13.0",
  "org.seleniumhq.selenium" % "selenium-support" % seleniumVersion,
  "org.seleniumhq.selenium" % "selenium-devtools-v130" % seleniumVersion,
  "com.lihaoyi" %% "sourcecode" % sourcecodeVersion,
  "com.fifesoft" % "rsyntaxtextarea" % rsyntaxtextareaVersion,
  "org.scalatest" %% "scalatest" % scalatestVersion % "test"
)

fork := true
outputStrategy := Some(StdoutOutput)
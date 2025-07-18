// Variables
val org: String = "com.outr"
val projectName: String = "robobrowser"
val githubOrg: String = "outr"
val email: String = "matt@matthicks.com"
val developerId: String = "darkfrog"
val developerName: String = "Matt Hicks"
val developerURL: String = "https://matthicks.com"

name := projectName
ThisBuild / organization := org
ThisBuild / version := "2.1.2"

val scala213: String = "2.13.16"
val scala3: String = "3.7.1"

ThisBuild / scalaVersion := scala3

ThisBuild / crossScalaVersions := List(scala213, scala3)

ThisBuild / scalacOptions ++= Seq("-unchecked", "-deprecation")

ThisBuild / sonatypeCredentialHost := xerial.sbt.Sonatype.sonatypeCentralHost
ThisBuild / publishMavenStyle := true
ThisBuild / publishTo := sonatypePublishToBundle.value
ThisBuild / sonatypeProfileName := org
ThisBuild / licenses := Seq("MIT" -> url(s"https://github.com/$githubOrg/$projectName/blob/master/LICENSE"))
ThisBuild / sonatypeProjectHosting := Some(xerial.sbt.Sonatype.GitHubHosting(githubOrg, projectName, email))
ThisBuild / homepage := Some(url(s"https://github.com/$githubOrg/$projectName"))
ThisBuild / scmInfo := Some(
  ScmInfo(
    url(s"https://github.com/$githubOrg/$projectName"),
    s"scm:git@github.com:$githubOrg/$projectName.git"
  )
)
ThisBuild / developers := List(
  Developer(id=developerId, name=developerName, email=email, url=url(developerURL))
)

ThisBuild / outputStrategy := Some(StdoutOutput)

ThisBuild / fork := true

ThisBuild / javaOptions ++= Seq(
  "--enable-native-access=ALL-UNNAMED",
  "--add-modules", "jdk.incubator.vector"
)

ThisBuild / Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oDF")

val scribeVersion: String = "3.16.1"

val rapidVersion: String = "0.16.0"

val spiceVersion: String = "0.10.3"

val seleniumVersion: String = "4.34.0"

val jsoupVersion: String = "1.21.1"

val appiumVersion: String = "9.5.0"

val sourcecodeVersion: String = "0.4.2"

val rsyntaxtextareaVersion: String = "3.6.0"

val scalatestVersion: String = "3.2.19"

val root = project.in(file("."))
  .aggregate(core, selenium, cdp)
  .settings(
    name := projectName,
    publish := {},
    publishLocal := {}
  )

lazy val core = project.in(file("core"))
  .settings(
    name := s"$projectName-core"
  )

lazy val selenium = project.in(file("selenium"))
  .dependsOn(core)
  .settings(
    name := s"$projectName-selenium",
    libraryDependencies ++= Seq(
      "com.outr" %% "scribe-slf4j2" % scribeVersion,
      "com.outr" %% "spice-client" % spiceVersion,
      "com.outr" %% "spice-server-undertow" % spiceVersion,
      "com.outr" %% "rapid-core" % rapidVersion,
      "com.outr" %% "rapid-scribe" % rapidVersion,
      "org.jsoup" % "jsoup" % jsoupVersion,
      "io.appium" % "java-client" % appiumVersion,
      "org.seleniumhq.selenium" % "selenium-api" % seleniumVersion,
      "org.seleniumhq.selenium" % "selenium-chrome-driver" % seleniumVersion,
      "org.seleniumhq.selenium" % "selenium-firefox-driver" % seleniumVersion,
      "org.seleniumhq.selenium" % "selenium-remote-driver" % seleniumVersion,
      "org.seleniumhq.selenium" % "htmlunit-driver" % "4.13.0",
      "org.seleniumhq.selenium" % "selenium-support" % seleniumVersion,
      "org.seleniumhq.selenium" % "selenium-devtools-v137" % seleniumVersion,
      "com.lihaoyi" %% "sourcecode" % sourcecodeVersion,
      "com.fifesoft" % "rsyntaxtextarea" % rsyntaxtextareaVersion,
      "org.scalatest" %% "scalatest" % scalatestVersion % "test"
    )
  )

lazy val cdp = project.in(file("cdp"))
  .dependsOn(core)
  .settings(
    name := s"$projectName-cdp",
    libraryDependencies ++= Seq(
      "com.outr" %% "spice-client" % spiceVersion,
      "com.outr" %% "spice-server-undertow" % spiceVersion,
      "com.outr" %% "rapid-core" % rapidVersion,
      "com.outr" %% "rapid-scribe" % rapidVersion,
      "org.jsoup" % "jsoup" % jsoupVersion,
      "org.scalatest" %% "scalatest" % scalatestVersion % Test,
      "com.outr" %% "rapid-test" % rapidVersion % Test,
      "com.outr" %% "spice-client-netty" % spiceVersion % Test,
    )
  )
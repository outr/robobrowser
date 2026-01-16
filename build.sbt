val org: String = "com.outr"
val projectName: String = "robobrowser"
val githubOrg: String = "outr"
val email: String = "matt@matthicks.com"
val developerId: String = "darkfrog"
val developerName: String = "Matt Hicks"
val developerURL: String = "https://matthicks.com"

name := projectName
ThisBuild / organization := org
ThisBuild / version := "2.3.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.7.4"

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

val scribeVersion: String = "3.17.0"

val rapidVersion: String = "2.3.2"

val spiceVersion: String = "0.10.19"

val jsoupVersion: String = "1.22.1"

val tikaVersion: String = "3.2.3"

val scalatestVersion: String = "3.2.19"

val root = project.in(file("."))
  .aggregate(core, cdp)
  .settings(
    name := projectName,
    publish := {},
    publishLocal := {}
  )

lazy val core = project.in(file("core"))
  .settings(
    name := s"$projectName-core"
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
      "org.apache.tika" % "tika-core" % tikaVersion,
      "org.apache.tika" % "tika-parsers-standard-package" % tikaVersion,
      "org.scalatest" %% "scalatest" % scalatestVersion % Test,
      "com.outr" %% "rapid-test" % rapidVersion % Test,
      "com.outr" %% "spice-client-netty" % spiceVersion % Test,
    )
  )
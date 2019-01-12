name := "robobrowser"
organization := "com.outr"
version := "1.0.0-SNAPSHOT"
scalaVersion := "2.12.7"
crossScalaVersions := Seq("2.12.7", "2.11.12")

libraryDependencies ++= Seq(
  "com.machinepublishers" % "jbrowserdriver" % "1.0.1",
  "io.youi" %% "youi-client" % "0.9.10"
)

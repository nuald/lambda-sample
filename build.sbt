import Dependencies._

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "com.github.nuald",
      scalaVersion := "2.12.3",
      version      := "0.1.0-SNAPSHOT"
    )),
    name := "Sandbox",
    scalacOptions ++= Seq("-deprecation", "-feature"),
    libraryDependencies ++= Seq(
      scalaTest % Test,
      "com.fasterxml.jackson.core" % "jackson-databind" % "2.9.1",
      "com.fasterxml.jackson.dataformat" % "jackson-dataformat-smile" % "2.9.1",
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.9.1",
      "com.datastax.cassandra" % "cassandra-driver-core" % "3.3.0",
      "com.lihaoyi" %% "ammonite-ops" % "1.0.3",
      "com.typesafe.akka" %% "akka-http" % "10.0.10",
      "org.eclipse.paho" % "mqtt-client" % "0.4.0",
      "org.slf4j" % "slf4j-api" % "1.7.5",
      "org.slf4j" % "slf4j-simple" % "1.7.5",
      "org.yaml" % "snakeyaml" % "1.19",
    ),
    resolvers += "MQTT Repository" at "https://repo.eclipse.org/content/repositories/paho-releases/"
  )

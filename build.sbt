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
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "com.fasterxml.jackson.core" % "jackson-databind" % "2.9.1",
      "com.fasterxml.jackson.dataformat" % "jackson-dataformat-smile" % "2.9.1",
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.9.1",
      "com.datastax.cassandra" % "cassandra-driver-core" % "3.3.0",
      "com.typesafe.akka" %% "akka-http" % "10.0.10",
      "com.typesafe.akka" %% "akka-slf4j" % "2.4.19",
      "org.clapper" %% "scalasti" % "3.0.1",
      "org.eclipse.paho" % "mqtt-client" % "0.4.0",
    ),
    resolvers += "MQTT Repository" at "https://repo.eclipse.org/content/repositories/paho-releases/"
  )

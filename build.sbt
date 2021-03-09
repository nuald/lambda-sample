name := "Sandbox"

version:= "0.1.1"

scalaVersion := "2.13.5"

scalacOptions ++= Seq("-deprecation", "-feature")

resolvers += "Eclipse Paho Repo" at "https://repo.eclipse.org/content/repositories/paho-releases/"

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.2.3",

  "com.datastax.oss" % "java-driver-core" % "4.10.0",
  "com.datastax.oss" % "java-driver-query-builder" % "4.10.0",

  "com.fasterxml.jackson.core" % "jackson-databind" % "2.12.2",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.12.2",
  "com.fasterxml.jackson.dataformat" % "jackson-dataformat-smile" % "2.12.2",
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % "2.12.2",

  "com.github.etaty" %% "rediscala" % "1.9.0",
  "com.github.haifengl" %% "smile-scala" % "2.6.0",
  "com.github.scopt" %% "scopt" % "4.0.1",

  "com.typesafe.akka" %% "akka-cluster" % "2.6.8",
  "com.typesafe.akka" %% "akka-slf4j" % "2.6.8",
  "com.typesafe.akka" %% "akka-http" % "10.2.2",

  "org.eclipse.paho" % "org.eclipse.paho.client.mqttv3" % "1.2.1",
  "org.antlr" % "ST4" % "4.3.1",

  "com.typesafe.akka" %% "akka-stream-testkit" % "2.6.8" % "test",
  "com.typesafe.akka" %% "akka-http-testkit" % "10.2.2" % "test",
  "org.scalatest" %% "scalatest" % "3.2.6" % "test"
)

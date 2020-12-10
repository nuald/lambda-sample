name := "Sandbox"

version:= "0.1.1"

scalaVersion := "2.13.4"

scalacOptions ++= Seq("-deprecation", "-feature")

resolvers += "Eclipse Paho Repo" at "https://repo.eclipse.org/content/repositories/paho-releases/"

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.datastax.oss" % "java-driver-core" % "4.9.0",
  "com.datastax.oss" % "java-driver-query-builder" % "4.9.0",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.12.0",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.12.0",
  "com.fasterxml.jackson.dataformat" % "jackson-dataformat-smile" % "2.12.0",
  "com.github.etaty" %% "rediscala" % "1.9.0",
  "com.github.haifengl" %% "smile-scala" % "2.6.0",
  "com.github.scopt" %% "scopt" % "4.0.0",

  "com.typesafe.akka" %% "akka-cluster" % "2.6.8",
  "com.typesafe.akka" %% "akka-slf4j" % "2.6.8",
  "com.typesafe.akka" %% "akka-http" % "10.2.1",

  "org.eclipse.paho" % "org.eclipse.paho.client.mqttv3" % "1.2.1",
  "org.antlr" % "ST4" % "4.3.1",

  "com.typesafe.akka" %% "akka-http-testkit" % "10.2.1" % "test",
  "org.scalatest" %% "scalatest" % "3.2.2" % "test"
)

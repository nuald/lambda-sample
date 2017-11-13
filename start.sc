#!/usr/bin/env amm

import $ivy.`com.github.scopt::scopt:3.7.0`
import $ivy.`org.clapper::scalasti:3.0.1`, org.clapper.scalasti.ST
import java.io._
import scala.io.Source
import scala.sys.process._
import scala.util._

case class Config(
  isClient: Boolean = false,
  host: String = "127.0.0.1"
)

val parser = new scopt.OptionParser[Config]("start.scala") {
  opt[String]("host").optional().valueName("<IP address>").
    action((x, c) => c.copy(host = x)).
    text("server host")

  cmd("client").action( (_, c) => c.copy(isClient = true)).
    text("Run the client.")

  cmd("server").action( (_, c) => c.copy(isClient = false)).
    text("Run the servers.")
}

val envDir = "target/env"
val LsofPattern = raw"""p(\d+)""".r

def isCassandraRunning: Boolean = {
  val lsof = Seq("lsof", "-Fp", "-i", ":9042")
  lsof.lineStream_!.map { (line) =>
    line match {
      case LsofPattern(_) => true
      case _ => false
    }
  }.exists(x => x)
}

def setupServers(host: String): Unit = {
  // Run Cassandra
  val src = Source.fromFile("resources/cassandra/cassandra.yaml").mkString
  val template = ST(src, '`', '`').add("host", host)
  template.render() match {
    case Success(dst) => {
      val cassandraYml = new File(s"$envDir/cassandra.yaml")
      new PrintWriter(cassandraYml) { write(dst); close }
      val url = cassandraYml.toURI().toURL()
      s"cassandra -f -Dcassandra.config=$url".run()
    }
    case Failure(ex) => println(ex)
  }
  // Run Redis
  s"redis-server --bind $host".run()
  // Run Mosquitto
  "mosquitto".run()
  // Waiting for Cassandra
  print("Waiting for Cassandra")
  while (!isCassandraRunning) {
    print(".")
    Thread.sleep(1000)
  }
  // Run the cluster
  val runOpt = s"run -c $host -r $host"
  Process("sbt", Seq(runOpt)).!
}

def setupClient(host: String): Unit = {

}

@main
def entrypoint(args: String*) = {
  parser.parse(args, Config()) match {
    case Some(config) =>
      new File(envDir).mkdir()
      if (config.isClient) {
        setupClient(config.host)
      } else {
        setupServers(config.host)
      }
    case None =>
    // arguments are bad, error message will have been displayed
  }
}

#!/usr/bin/env scala

import java.io._
import scala.io.Source
import scala.sys.process._
import scala.util._

def isCassandraRunning: Boolean = {
  val lsofPattern = raw"""p(\d+)""".r
  val lsof = Seq("lsof", "-Fp", "-i", ":9042")
  lsof.lineStream_!.map { (line) =>
    line match {
      case lsofPattern(_) => true
      case _ => false
    }
  }.exists(x => x)
}

def setupServers(host: String): Unit = {
  // Run Cassandra
  val src = Source.fromFile("resources/cassandra/cassandra.yaml").mkString
  val dst = src.replaceAll("`host`", host)
  val envDir = "target/env"
  new File(envDir).mkdir()
  val cassandraYml = new File(s"$envDir/cassandra.yaml")
  new PrintWriter(cassandraYml) { write(dst); close }
  val url = cassandraYml.toURI().toURL()
  s"cassandra -f -Dcassandra.config=$url".run()
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

def usage(): Unit = println("""scala start.sc [server|client] --host=<host>

Cluster helper: runs either servers or client with the provided server host.
""")

def entrypoint(args: Array[String]): Unit = {
  var isClientOpt: Option[Boolean] = None
  var host = "127.0.0.1"
  val hostPattern = raw"""--host=(\d+\.\d+\.\d+\.\d+)""".r

  args foreach (_ match {
    case "server" => isClientOpt = Some(false)
    case "client" => isClientOpt = Some(true)
    case hostPattern(h) => host = h
    case _ => // pass
  })

  isClientOpt match {
    case Some(isClient) =>
      if (isClient) {
        setupClient(host)
      } else {
        setupServers(host)
      }
    case None => usage()
  }
}

entrypoint(args)

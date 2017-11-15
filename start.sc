#!/usr/bin/env scala

import java.io._
import scala.io.Source
import scala.sys.process._

val EnvDir = "target/env"

def isCassandraRunning: Boolean = {
  val lsofPattern = raw"""p(\d+)""".r
  val lsof = Seq("lsof", "-Fp", "-i", ":9042")
  lsof.lineStream_!.map {
    case lsofPattern(_) => true
    case _ => false
  }.exists(x => x)
}

def runCmdAsync(cmd: String, dryRun: Boolean): Unit = {
  if (dryRun) {
    println(cmd)
  } else {
    cmd.run()
  }
}

def runSbt(cmd: String, dryRun: Boolean): Unit = {
  val isWindows = sys.props("os.name").startsWith("Windows")
  val shellPrefix: Array[String] = if (isWindows) Array("cmd", "/C") else Array()
  val runSeq = shellPrefix ++ Array("sbt", cmd)
  if (dryRun) {
    println(runSeq.mkString(" "))
  } else {
    Process(runSeq).!
  }
}

def createAkkaConfig(host: String, port: Int, isClient: Boolean): File = {
  val src = Source.fromFile("resources/akka/cluster.conf").mkString
  val role = if (isClient) "" else "frontend"
  val dst = src.replaceAll("<host>", host)
    .replaceAll("<port>", port.toString)
    .replaceAll("<role>", role)
  val akkaConf = new File(s"$EnvDir/cluster.conf")
  new PrintWriter(akkaConf) { write(dst); close() }
  akkaConf
}

def setupServers(host: String, dryRun: Boolean): Unit = {
  // Run Cassandra
  val src = Source.fromFile("resources/cassandra/cassandra.yaml").mkString
  val dst = src.replaceAll("<host>", host)
  val cassandraYml = new File(s"$EnvDir/cassandra.yaml")
  new PrintWriter(cassandraYml) { write(dst); close() }
  val url = cassandraYml.toURI.toURL
  runCmdAsync(s"cassandra -f -Dcassandra.config=$url", dryRun)

  // Run Redis
  runCmdAsync(s"redis-server --bind $host", dryRun)

  // Run Mosquitto
  runCmdAsync("mosquitto", dryRun)

  if (!dryRun) {
    // Waiting for Cassandra
    print("Waiting for Cassandra")
    while (!isCassandraRunning) {
      print(".")
      Thread.sleep(1000)
    }
  }

  // Run the cluster
  val conf = createAkkaConfig(host, 2551, isClient = false)
  runSbt(s"run -c $host -r $host --config $conf", dryRun)
}

def setupClient(host: String, port: Int, dryRun: Boolean): Unit = {
  // Run the client
  val conf = createAkkaConfig(host, port, isClient = true)
  runSbt(s"run --client -c $host -r $host --config $conf", dryRun)
}

def usage(): Unit = println("""
scala start.sc [server|client] [--host=<host>] [--port=<port>] [--dry-run]

Cluster helper: runs either servers or client with the provided server host.
The port is applicable only for the client mode (specifies the seed port).
You may use "--dry-run" option to verify the generation of config files.
""")

def entrypoint(args: Array[String]): Unit = {
  val hostPattern = raw"""--host=(\d+\.\d+\.\d+\.\d+)""".r
  val portPattern = raw"""--port=(\d+)""".r

  var isClientOpt: Option[Boolean] = None
  var host = "127.0.0.1"
  var port = 2552
  var dryRun = false

  args foreach {
    case "server" => isClientOpt = Some(false)
    case "client" => isClientOpt = Some(true)
    case "--dry-run" => dryRun = true
    case hostPattern(h) => host = h
    case portPattern(p) => port = p.toInt
    case _ => // pass
  }

  isClientOpt match {
    case Some(isClient) =>
      new File(EnvDir).mkdir()
      if (isClient) {
        setupClient(host, port, dryRun)
      } else {
        setupServers(host, dryRun)
      }
    case None => usage()
  }
}

entrypoint(args)

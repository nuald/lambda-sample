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
  println("$ " + cmd)
  if (!dryRun) {
    Runtime.getRuntime.exec(cmd)
  }
}

def runSbt(cmd: String, dryRun: Boolean): Unit = {
  val isWindows = sys.props("os.name").startsWith("Windows")
  val shellPrefix: Array[String] = if (isWindows) Array("cmd", "/C") else Array()
  val runSeq = shellPrefix ++ Array("sbt", cmd)
  println("\n$ " + runSeq.mkString(" "))
  if (!dryRun) {
    Process(runSeq).!
  }
}

def createAkkaConfig(serverHost: String,
                     clientHost: String,
                     clientPort: Int,
                     isClient: Boolean): File = {
  val src = Source.fromFile("resources/akka/cluster.conf").mkString
  val role = if (isClient) "" else "frontend"
  val dst = src.replaceAll("<server-host>", serverHost)
    .replaceAll("<client-host>", clientHost)
    .replaceAll("<client-port>", clientPort.toString)
    .replaceAll("<role>", role)
  val akkaConf = new File(s"$EnvDir/cluster.conf")
  new PrintWriter(akkaConf) { write(dst); close() }
  akkaConf
}

def setupServers(serverHost: String,
                 noLocalAnalyzer: Boolean,
                 dryRun: Boolean): Unit = {
  // Run Cassandra
  val src = Source.fromFile("resources/cassandra/cassandra.yaml").mkString
  val dst = src.replaceAll("<host>", serverHost)
  val cassandraYml = new File(s"$EnvDir/cassandra.yaml")
  new PrintWriter(cassandraYml) { write(dst); close() }
  val url = cassandraYml.toURI.toURL
  runCmdAsync(s"cassandra -f -Dcassandra.config=$url", dryRun)

  // Run Redis
  runCmdAsync(s"redis-server --bind $serverHost", dryRun)

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
  val conf = createAkkaConfig(serverHost, serverHost, 2551, isClient = false)
  val opts = Seq("run",
    "-c", serverHost,
    "-r", serverHost,
    "--config", conf,
    if (noLocalAnalyzer) "--no-local-analyzer" else ""
  )
  runSbt(opts.mkString(" "), dryRun)
}

def setupClient(serverHost: String,
                clientHost: String,
                clientPort: Int,
                dryRun: Boolean): Unit = {
  // Run the client
  val conf = createAkkaConfig(serverHost, clientHost, clientPort, isClient = true)
  runSbt(s"run --client -c $serverHost -r $serverHost --config $conf", dryRun)
}

def usage(): Unit = println("""
scala start.sc [server|client] [options]

Cluster helper: runs either servers or client with the provided server host.

Options:

  --server-host=<host> Server IP address (used for both modes)
  --client-host=<port> Client IP address (used for client mode only)
  --client-port=<port> Client TCP port (used for client mode only)
  --no-local-analyzer  Don't use the local analyzer (used for server mode only)
  --dry-run            Only update the config files
""")

def entrypoint(args: Array[String]): Unit = {
  val serverHostPattern = raw"""--server-host=(\d+\.\d+\.\d+\.\d+)""".r
  val clientHostPattern = raw"""--client-host=(\d+\.\d+\.\d+\.\d+)""".r
  val clientPortPattern = raw"""--client-port=(\d+)""".r

  var isClientOpt: Option[Boolean] = None
  var serverHost = "127.0.0.1"
  var clientHost = "127.0.0.1"
  var clientPort = 2552
  var dryRun = false
  var noLocalAnalyzer = false

  args foreach {
    case "server" => isClientOpt = Some(false)
    case "client" => isClientOpt = Some(true)
    case "--dry-run" => dryRun = true
    case "--no-local-analyzer" => noLocalAnalyzer = true
    case serverHostPattern(h) => serverHost = h
    case clientHostPattern(h) => clientHost = h
    case clientPortPattern(p) => clientPort = p.toInt
    case _ => // pass
  }

  isClientOpt match {
    case Some(isClient) =>
      new File(EnvDir).mkdir()
      if (isClient) {
        setupClient(serverHost, clientHost, clientPort, dryRun)
      } else {
        setupServers(serverHost, noLocalAnalyzer, dryRun)
      }
    case None => usage()
  }
}

entrypoint(args)

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import analyzer.{Analyzer, Endpoint, HistoryWriter, Trainer}
import com.datastax.driver.core.Cluster
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration._
import dashboard._
import mqtt._
import lib._
import redis.RedisClient

import scala.io.Source

object Main extends App {
  val conf = Config.get

  case class ScoptConfig(
    cassandraHost: String = conf.cassandra.address,
    akkaConfig: String = "",
    isServer: Boolean = true,
    noLocalAnalyzer: Boolean = false,
    redisHost: String = conf.redis.address)

  val parser = new scopt.OptionParser[ScoptConfig]("""sbt "run [options]" """) {
    opt[String]('c', "cassandra").optional().valueName("<Cassandra host>").
      action((x, c) => c.copy(cassandraHost = x)).
      text(s"Cassandra host (${ conf.cassandra.address } by default)")

    opt[String]('r', "redis").optional().valueName("<Redis host>").
      action((x, c) => c.copy(redisHost = x)).
      text(s"Redis host (${ conf.redis.address } by default)")

    opt[String]("config").optional().valueName("<Akka config>").
      action((x, c) => c.copy(akkaConfig = x)).
      text(s"Akka custom config")

    opt[Unit]("client")
      .action((_, c) => c.copy(isServer = false)).text("Cluster client mode")

    opt[Unit]("no-local-analyzer")
      .action((_, c) => c.copy(noLocalAnalyzer = true)).text("Don't use the local analyzer")
  }

  parser.parse(args, ScoptConfig()) match {
    case Some(scoptConfig) =>
      val akkaConfig = if (scoptConfig.akkaConfig.nonEmpty) {
        val content = Source.fromFile(scoptConfig.akkaConfig).mkString
        ConfigFactory.parseString(content).withFallback(ConfigFactory.load())
      } else {
        ConfigFactory.load()
      }

      implicit val system: ActorSystem = ActorSystem("cluster", akkaConfig)
      implicit val materializer: ActorMaterializer = ActorMaterializer()

      val cluster = Cluster.builder().addContactPoint(scoptConfig.cassandraHost).build()
      val cassandraClient = system.actorOf(CassandraClient.props(cluster), "cassandra-client")

      val redisClient = RedisClient(scoptConfig.redisHost, conf.redis.port)

      val clientHost = akkaConfig.getString("akka.remote.netty.tcp.hostname")
      val analyzerName = if (clientHost.isEmpty) "analyzer" else s"analyzer:$clientHost"
      val analyzerOpt = if (scoptConfig.noLocalAnalyzer) None else
        Some(system.actorOf(Analyzer.props(cassandraClient, redisClient), analyzerName))

      if (scoptConfig.isServer) {
        system.actorOf(Producer.props(), "producer")
        system.actorOf(Consumer.props(cluster), "consumer")

        val endpoint = system.actorOf(Endpoint.props(analyzerOpt), "endpoint")
        system.actorOf(Trainer.props(cassandraClient, redisClient), "trainer")

        system.actorOf(HistoryWriter.props(cluster, redisClient, analyzerOpt), "history-writer")
        system.actorOf(Dashboard.props(cassandraClient, endpoint), "dashboard")
      }

      scala.sys.addShutdownHook {
        system.terminate()
        Await.result(system.whenTerminated, 5.seconds)
        cluster.close()
      }
    case None =>
      // arguments are bad, error message will have been displayed
  }
}

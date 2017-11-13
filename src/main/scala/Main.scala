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

object Main extends App {
  val DefaultPort = 2551

  val conf = Config.get

  case class ScoptConfig(cassandraHost: String = conf.cassandra.address,
                         isClient: Boolean = false,
                         redisHost: String = conf.redis.address,
                         port: Int = DefaultPort)

  val parser = new scopt.OptionParser[ScoptConfig]("""sbt "run [options]" """) {
    opt[String]('c', "cassandra").optional().valueName("<Cassandra host>").
      action( (x, c) => c.copy(cassandraHost = x) ).
      text(s"Cassandra host (${ conf.cassandra.address } by default)")

    opt[String]('r', "redis").optional().valueName("<Redis host>").
      action( (x, c) => c.copy(redisHost = x) ).
      text(s"Redis host (${ conf.redis.address } by default)")

    opt[Unit]("client").action( (_, c) =>
      c.copy(isClient = true) ).text("Cluster client mode")

    opt[String]('p', "port").optional().valueName("<port>").
      action( (x, c) => c.copy(port = x.toInt) ).
      text(s"Local port ($DefaultPort by default)")
  }

  parser.parse(args, ScoptConfig()) match {
    case Some(scoptConfig) =>
      val cluster = Cluster.builder().addContactPoint(scoptConfig.cassandraHost).build()

      if (scoptConfig.isClient) {
        val akkaConfig = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=${ scoptConfig.port }").
          withFallback(ConfigFactory.load())

        implicit val system: ActorSystem = ActorSystem("ClusterSystem", akkaConfig)
        implicit val materializer: ActorMaterializer = ActorMaterializer()

        val cassandraClient = system.actorOf(CassandraClient.props(cluster), "cassandra-client")
        val redisClient = RedisClient(scoptConfig.redisHost, conf.redis.port)
        system.actorOf(Analyzer.props(cassandraClient, redisClient), "analyzer")

        scala.sys.addShutdownHook {
          system.terminate()
          Await.result(system.whenTerminated, 5.seconds)
          cluster.close()
        }
      } else {
        val akkaConfig = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=${ scoptConfig.port }").
          withFallback(ConfigFactory.parseString("akka.cluster.roles = [frontend]")).
          withFallback(ConfigFactory.load())

        implicit val system: ActorSystem = ActorSystem("ClusterSystem", akkaConfig)
        implicit val materializer: ActorMaterializer = ActorMaterializer()

        val cassandraClient = system.actorOf(CassandraClient.props(cluster), "cassandra-client")
        val redisClient = RedisClient(scoptConfig.redisHost, conf.redis.port)
        system.actorOf(Producer.props(), "producer")
        system.actorOf(Consumer.props(cluster), "consumer")

        system.actorOf(Analyzer.props(cassandraClient, redisClient), "analyzer")
        system.actorOf(Endpoint.props(), "endpoint")
        system.actorOf(Trainer.props(cassandraClient, redisClient), "trainer")
        system.actorOf(HistoryWriter.props(cluster, redisClient), "history-writer")

        system.actorOf(Dashboard.props(cassandraClient), "dashboard")

        scala.sys.addShutdownHook {
          system.terminate()
          Await.result(system.whenTerminated, 5.seconds)
          cluster.close()
        }
      }

    case None =>
      // arguments are bad, error message will have been displayed
  }
}

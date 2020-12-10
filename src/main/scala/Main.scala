import akka.actor.ActorSystem
import akka.event.{LogSource, Logging, LoggingAdapter}
import analyzer.{Analyzer, Endpoint, HistoryWriter, Trainer}
import com.datastax.oss.driver.api.core.CqlSession
import com.typesafe.config.ConfigFactory

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import dashboard._
import mqtt._
import lib._
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import redis.RedisClient
import java.net.InetSocketAddress

import scala.io.Source
import scala.util.{Failure, Success}

object Main extends App {
  private[this] val conf = Config.get

  case class ScoptConfig(
    cassandraHost: String = conf.cassandra.address,
    akkaConfig: String = "",
    isServer: Boolean = true,
    noLocalAnalyzer: Boolean = false,
    redisHost: String = conf.redis.address)

  private[this] val parser = new scopt.OptionParser[ScoptConfig]("""sbt "run [options]" """) {
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

  private[this] def getCassandraSession(contactPoint: String)
    (implicit logger: LoggingAdapter): Option[CqlSession] = {
    val session = CqlSession.builder()
      .addContactPoint(new InetSocketAddress(contactPoint, 9042))
      .withKeyspace(conf.cassandra.keyspace)
      .build()
    try {
      val clusterName = session.getMetadata.getClusterName
      logger.info(s"Cassandra cluster: $clusterName")
      Some(session)
    } catch {
      case e: Throwable =>
        logger.error(e, "Cassandra session is not available")
        None
    }
  }

  private[this] def getConnectedMqtt(implicit logger: LoggingAdapter): Option[MqttClient] = {
    val mqttClient = new MqttClient(conf.mqtt.broker,
      MqttClient.generateClientId, new MemoryPersistence)
    try {
      mqttClient.connect()
      Some(mqttClient)
    } catch {
      case e: Throwable =>
        logger.error(e, "MQTT server is not available")
        None
    }
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
      implicit val executionContext: ExecutionContext = system.dispatcher

      // Setup logging
      implicit val logSource: LogSource[AnyRef] = new LogSource[AnyRef] {
        def genString(o: AnyRef): String = o.getClass.getName
        override def getClazz(o: AnyRef): Class[_] = o.getClass
      }
      implicit val logger: LoggingAdapter = Logging(system, this)

      def getRedisClient(host: String): Option[RedisClient] = {
        val redisClient = RedisClient(host, conf.redis.port)
        Await.ready(redisClient.ping(), 1.seconds).value.get match {
          case Success(_) => Some(redisClient)
          case Failure(e) =>
            system.stop(redisClient.redisConnection)
            logger.error(e, "Redis server is not available")
            None
        }
      }

      getCassandraSession(scoptConfig.cassandraHost) match {
        case Some(session) =>
          val cassandraClient = new CassandraClient(session)

          getRedisClient(scoptConfig.redisHost) foreach { redisClient =>
            val analyzerOpt = if (scoptConfig.noLocalAnalyzer) None else
              Some(system.actorOf(Analyzer.props(cassandraClient, redisClient), "analyzer"))

            if (scoptConfig.isServer) {
              val endpoint = system.actorOf(Endpoint.props(analyzerOpt), "endpoint")
              system.actorOf(Trainer.props(cassandraClient, redisClient), "trainer")

              system.actorOf(HistoryWriter.props(session, redisClient, analyzerOpt), "history-writer")
              val dashboard = system.actorOf(Dashboard.props(cassandraClient, endpoint), "dashboard")

              endpoint ! HttpStart
              dashboard ! HttpStart
            }
          }

          if (scoptConfig.isServer) {
            getConnectedMqtt foreach { mqttClient =>
              val producer = system.actorOf(Producer.props(mqttClient), "producer")
              system.actorOf(Consumer.props(mqttClient, session), "consumer")

              producer ! HttpStart
            }
          }

          scala.sys.addShutdownHook {
            system.terminate()
            Await.result(system.whenTerminated, 5.seconds)
            session.close()
          }
        case None => system.terminate()
      }

    case None =>
      // arguments are bad, error message will have been displayed
  }
}

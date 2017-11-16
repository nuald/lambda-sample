package analyzer

import akka.actor._
import akka.pattern.ask
import akka.event.LoggingAdapter
import akka.stream.ActorMaterializer
import akka.util.Timeout
import lib.CassandraClient.{Entry, Full}
import lib._
import redis.RedisClient
import smile.classification.{RandomForest, randomForest}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

object Trainer {
  def props(cassandraClient: ActorRef, redisClient: RedisClient)
           (implicit materializer: ActorMaterializer) =
    Props(classOf[Trainer], cassandraClient, redisClient, materializer)

  private final case object Tick
}

class Trainer(cassandraClient: ActorRef, redisClient: RedisClient)
             (implicit materializer: ActorMaterializer)
  extends Actor with ActorLogging {
  import Trainer._

  implicit val system: ActorSystem = context.system
  implicit val executionContext: ExecutionContext = system.dispatcher
  implicit val logger: LoggingAdapter = log

  private val conf = Config.get
  implicit val timeout: Timeout = Timeout(conf.fullAnalyzer.timeout.millis)

  def createFittedModel(entries: List[Entry]): Try[RandomForest] = {
    // Features are multi-dimensional, labels are integers
    val mapping = (x: Entry) => (Array(x.value), x.anomaly)

    // Extract the features and the labels
    val (features, labels) = entries.map(mapping).unzip

    // Fit the model
    Try(randomForest(features.toArray, labels.toArray))
  }

  override def receive: Receive = {
    case Tick =>
      val serializer = new ClusterSerializer()
      val futures =
        for (sensor <- conf.mqtt.sensors.asScala)
          yield for {
            entries <- ask(cassandraClient, Full(sensor)).mapTo[List[Entry]]
          } yield {
            createFittedModel(entries) match {
              case Success(rf) =>
                val bytes = serializer.toBinary(rf)
                redisClient.hset(conf.fullAnalyzer.key, sensor, bytes)
              case Failure(e) =>
                log.error(e, s"Fitting model for $sensor failed:")
            }
          }

      Future.sequence(futures) foreach { _ =>
        system.scheduler.scheduleOnce(conf.fullAnalyzer.period.millis) {
          self ! Tick
        }
      }
  }

  system.scheduler.scheduleOnce(conf.fullAnalyzer.period.millis) {
    self ! Tick
  }
}

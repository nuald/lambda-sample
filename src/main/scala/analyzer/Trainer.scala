package analyzer

import akka.actor._
import akka.event.LoggingAdapter
import akka.stream.ActorMaterializer
import akka.util.Timeout
import lib._
import redis.RedisClient
import smile.classification.{RandomForest, randomForest}
import smile.data._
import smile.data.formula._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}
import collection.JavaConverters._

object Trainer {
  def props(cassandraClient: CassandraClient, redisClient: RedisClient)
           (implicit materializer: ActorMaterializer) =
    Props(classOf[Trainer], cassandraClient, redisClient, materializer)

  private final case object Tick
}

class Trainer(cassandraClient: CassandraClient, redisClient: RedisClient)
             (implicit materializer: ActorMaterializer)
  extends Actor with ActorLogging {
  import Trainer._

  private[this] val conf = Config.get

  implicit val system: ActorSystem = context.system
  implicit val executionContext: ExecutionContext = system.dispatcher
  implicit val logger: LoggingAdapter = log
  implicit val timeout: Timeout = Timeout(conf.fullAnalyzer.timeout.millis)

  override def receive: Receive = {
    case Tick =>
      val serializer = new BinarySerializer()
      val futures =
        for (sensor <- conf.mqtt.sensorsList)
          yield {
            createFittedModel(cassandraClient.full(sensor)) match {
              case Success(rf) =>
                val bytes = serializer.toBinary(rf)
                redisClient.hset(conf.fullAnalyzer.key, sensor, bytes)
              case Failure(e) =>
                log.error(e, s"Fitting model for $sensor failed:")
            }
          }

      futures foreach { _ =>
        system.scheduler.scheduleOnce(conf.fullAnalyzer.period.millis) {
          self ! Tick
        }
      }
  }

  private[this] def createFittedModel(entries: Iterable[Entry]): Try[RandomForest] = {
    val data = DataFrame.of(entries.toList.asJava, classOf[Entry])
    val formula = "anomaly" ~ "value"

    // Fit the model
    Try(randomForest(formula, data))
  }

  system.scheduler.scheduleOnce(conf.fullAnalyzer.period.millis) {
    self ! Tick
  }
}

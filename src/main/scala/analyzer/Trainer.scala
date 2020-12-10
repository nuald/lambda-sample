package analyzer

import akka.actor._
import akka.event.LoggingAdapter
import akka.util.Timeout
import lib._
import redis.RedisClient

import smile.classification.{RandomForest, randomForest}
import smile.data._
import smile.data.formula._
import smile.data.`type`._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}
import scala.jdk.CollectionConverters._

object Trainer {
  def props(cassandraClient: CassandraClient, redisClient: RedisClient) =
    Props(classOf[Trainer], cassandraClient, redisClient)

  private final case object Tick
}

class Trainer(cassandraClient: CassandraClient, redisClient: RedisClient)
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
    val data = DataFrame.of(
      entries.toList
        .map(row => Tuple.of(
          Array(
            row.value.asInstanceOf[AnyRef],
            row.anomaly.asInstanceOf[AnyRef]),
          DataTypes.struct(
            new StructField("value", DataTypes.DoubleType),
            new StructField("anomaly", DataTypes.IntegerType))))
        .asJava)
    val formula = "anomaly" ~ "value"

    // Fit the model
    Try(randomForest(formula, data))
  }

  system.scheduler.scheduleOnce(conf.fullAnalyzer.period.millis) {
    self ! Tick
  }
}

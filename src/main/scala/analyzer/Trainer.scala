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

object Trainer {
  def props(cassandraClient: ActorRef)(implicit materializer: ActorMaterializer) =
    Props(classOf[Trainer], cassandraClient, materializer)

  private final case object Tick
}

class Trainer(cassandraClient: ActorRef)(implicit materializer: ActorMaterializer)
  extends Actor with ActorLogging {
  import Trainer._

  implicit val system: ActorSystem = context.system
  implicit val executionContext: ExecutionContext = system.dispatcher
  implicit val logger: LoggingAdapter = log

  private val conf = Config.get
  val r = RedisClient(conf.redis.address, conf.redis.port)
  private val sealWriter = new Sealed[RandomForest](conf.redis.salt).writer
  implicit val timeout: Timeout = Timeout(conf.fullAnalyzer.timeout.millis)

  def createFittedModel(entries: List[Entry]): RandomForest = {
    // Features are multi-dimensional, labels are integers
    val mapping = (x: Entry) => (Array(x.value), x.anomaly)

    // Extract the features and the labels
    val (features, labels) = entries.map(mapping).unzip

    // Fit the model
    randomForest(features.toArray, labels.toArray)
  }

  override def receive: Receive = {
    case Tick =>
      val futures =
        for (sensor <- conf.mqtt.sensors.asScala)
          yield for {
            entries <- ask(cassandraClient, Full(sensor)).mapTo[List[Entry]]
          } yield {
            val rf = createFittedModel(entries)
            sealWriter(rf) foreach { bytes =>
              r.hset(conf.fullAnalyzer.key, sensor, bytes)
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

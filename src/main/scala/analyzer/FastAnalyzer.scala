package analyzer

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.event.LoggingAdapter
import akka.pattern.{ask, pipe}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import analyzer.Endpoint.Analyze
import lib.CassandraClient.{Entry, Recent}
import lib.{Config, Sealed}
import redis.RedisClient

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.collection.JavaConverters._

final case class SensorMeta(name: String, ts: java.util.Date, anomaly: Double) extends Serializable

object FastAnalyzer {
  def props(cassandraClient: ActorRef)(implicit materializer: ActorMaterializer) =
    Props(classOf[FastAnalyzer], cassandraClient, materializer)

  def getAnomaly(value: Double, values: List[Double]): Double = {
    val size = values.size
    val avg = values.sum / size
    val stddev = math.sqrt(
      values.map(x => math.pow(x - avg, 2)).sum / size
    )
    val recentDev = math.abs(value - avg)
    val anomaly = (recentDev - stddev) / (2 * stddev)
    if (anomaly < 0) {
      0
    } else if (anomaly > 1) {
      1
    } else {
      anomaly
    }
  }
}

class FastAnalyzer(cassandraClient: ActorRef)(implicit materializer: ActorMaterializer)
  extends Actor with ActorLogging {

  implicit val system: ActorSystem = context.system
  implicit val executionContext: ExecutionContext = system.dispatcher
  implicit val logger: LoggingAdapter = log

  private val conf = Config.get
  private val sealWriter = new Sealed[SensorMeta](conf.redis.salt).writer
  val r = RedisClient(conf.redis.address, conf.redis.port)
  implicit val timeout: Timeout = Timeout(conf.fastAnalyzer.timeout.millis)

  def analyze(entries: List[Entry]): Double = {
    val values = entries.map(_.value)
    FastAnalyzer.getAnomaly(values.head, values)
  }

  override def receive: Receive = {
    case Analyze =>
      val futures =
        for (sensor <- conf.mqtt.sensors.asScala)
          yield for {
            entries <- ask(cassandraClient, Recent(sensor)).mapTo[List[Entry]]
          } yield {
            val meta = SensorMeta(
              sensor,
              new java.util.Date(System.currentTimeMillis),
              analyze(entries)
            )
            sealWriter(meta) foreach { bytes =>
              r.hset(conf.fastAnalyzer.key, sensor, bytes)
            }
            meta
          }

      Future.sequence(futures) pipeTo sender()
  }
}

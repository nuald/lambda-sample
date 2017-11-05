package analyzer

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.pattern.{ask, pipe}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import analyzer.Endpoint.Analyze
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.smile.SmileFactory
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.redis._
import lib.CassandraClient.{Entry, Recent}
import lib.Config

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.collection.JavaConverters._

final case class SensorMeta(name: String, ts: java.util.Date, anomaly: Double) {
  def toBytes: Array[Byte] = {
    val mapper = new ObjectMapper(new SmileFactory) with ScalaObjectMapper
    mapper.registerModule(DefaultScalaModule)
    mapper.writeValueAsBytes(this)
  }
}

object SensorMeta {
  val mapper = new ObjectMapper(new SmileFactory) with ScalaObjectMapper
  mapper.registerModule(DefaultScalaModule)

  def get(bytes: Array[Byte]): SensorMeta = mapper.readValue[SensorMeta](bytes)
}

object FastAnalyzer {
  def props(cassandraClient: ActorRef)(implicit materializer: ActorMaterializer) =
    Props(classOf[FastAnalyzer], cassandraClient, materializer)
}

class FastAnalyzer(cassandraClient: ActorRef)(implicit materializer: ActorMaterializer)
  extends Actor with ActorLogging {

  implicit val system: ActorSystem = context.system
  implicit val executionContext: ExecutionContext = system.dispatcher

  private val conf = Config.get
  val r = new RedisClient(conf.redis.address, conf.redis.port)
  implicit val timeout: Timeout = Timeout(conf.fastAnalyzer.timeout.millis)

  def analyze(entries: List[Entry]): Double = {
    val values = entries.map(_.value)
    val size = values.size
    val avg = values.sum / size
    val stddev = math.sqrt(
      values.map(x => math.pow(x - avg, 2)).sum / size
    )
    val recentDev = math.abs(values.head - avg)
    val anomaly = (recentDev - stddev) / (2 * stddev)
    if (anomaly < 0) {
      0
    } else if (anomaly > 1) {
      1
    } else {
      anomaly
    }
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
            Future {
              r.hset(conf.fastAnalyzer.key, sensor, meta.toBytes)
            }
            meta
          }

      Future.sequence(futures) pipeTo sender()
  }
}

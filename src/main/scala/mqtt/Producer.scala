package mqtt

import akka.actor._
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import org.clapper.scalasti.ST
import org.eclipse.paho.client.mqttv3._
import lib._

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.io.Source
import scala.util.{Failure, Success}

object Producer {
  def props(mqttClient: MqttClient)
           (implicit materializer: ActorMaterializer) =
    Props(classOf[Producer], mqttClient, materializer)

  final case class MqttEntry(sensor: String, value: Double, anomaly: Int)
  final case class SensorModel(name: String, isNormal: Boolean)

  private final case object Tick
}

class Producer(mqttClient: MqttClient)
              (implicit materializer: ActorMaterializer)
  extends Actor with ActorLogging {
  import Producer._

  implicit val system: ActorSystem = context.system
  implicit val executionContext: ExecutionContext = system.dispatcher
  implicit val logger: LoggingAdapter = log

  private val conf = Config.get
  private val msgTopic = mqttClient.getTopic(conf.mqtt.topic)

  private val sensors = conf.mqtt.sensors.asScala
  private var state = sensors.map(k => (k, "normal")).toMap

  var httpBinding: Option[ServerBinding] = None
  var httpClient: Option[HttpClient] = None

  override def postStop(): Unit = {
    httpBinding match {
      case Some(x) => x.unbind
      case None =>
    }
    mqttClient.disconnect()
  }

  override def receive: Receive = {
    case Tick =>
      val r = scala.util.Random
      val bound = conf.mqtt.bound
      val serializer = new BinarySerializer()

      for (sensor <- sensors) {
        val sensorState = state(sensor)
        val sign = if (r.nextBoolean()) -1 else 1
        val value = sign * (sensorState match {
          case "normal" => r.nextInt(bound)
          case "anomaly" => bound + r.nextInt(bound / 2)
        })
        val entry = MqttEntry(
          sensor,
          value,
          if (sensorState == "anomaly") 1 else 0
        )
        val bytes = serializer.toBinary(entry)
        msgTopic.publish(new MqttMessage(bytes))
      }

    case HttpStart =>
      httpClient = Some(new HttpClient(
        conf.producer.address,
        conf.producer.port,
        None,
        self
      ))

    case HttpRoute =>
      sender() ! path("update") {
        post {
          formFieldMap { fields =>
            state = fields
            complete("OK")
          }
        }
      } ~
        pathSingleSlash {
          get {
            val src = Source.fromFile("resources/producer/index.html").mkString
            val model = sensors.map(name => SensorModel(name, state(name) == "normal"))
            val template = ST(src, '$', '$').add("sensors", model)
            template.render() match {
              case Success(dst) =>
                complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, dst))
              case Failure(ex) => complete(StatusCodes.InternalServerError, ex)
            }
          }
        }

    case HttpConnected(binding) =>
      httpBinding = Some(binding)

    case HttpConnectionFailure(ex) =>
      log.error(s"Failed to establish HTTP connection $ex")
  }

  system.scheduler.schedule(0.millis, conf.mqtt.timeout.millis) {
    self ! Tick
  }
}

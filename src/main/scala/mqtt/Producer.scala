package mqtt

import akka.actor._
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import lib._
import org.eclipse.paho.client.mqttv3._
import org.stringtemplate.v4._

import scala.beans.BeanProperty
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.io.Source
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success}

object Producer {
  def props(mqttClient: MqttClient) =
    Props(classOf[Producer], mqttClient)

  final case class MqttEntry(sensor: String, value: Double, anomaly: Int)
  final case class SensorModel(
    @BeanProperty name: String,
    @BeanProperty isNormal: Boolean)

  private final case object Tick
}

class Producer(mqttClient: MqttClient)
  extends Actor with ActorLogging {
  import Producer._

  private[this] val conf = Config.get
  private[this] val msgTopic = mqttClient.getTopic(conf.mqtt.topic)
  private[this] val sensors = conf.mqtt.sensorsList
  private[this] var state = sensors.map(k => (k, "normal")).toMap
  private[this] var httpBinding: Option[ServerBinding] = None
  private[this] var httpClient: Option[HttpClient] = None

  implicit val system: ActorSystem = context.system
  implicit val executionContext: ExecutionContext = system.dispatcher
  implicit val logger: LoggingAdapter = log

  override def postStop(): Unit = {
    httpBinding match {
      case Some(x) => x.unbind()
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
            val template = new ST(src, '$', '$').add("sensors", model.asJava)
            val dst = template.render()
            complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, dst))
          }
        }

    case HttpConnected(binding) =>
      httpBinding = Some(binding)

    case HttpConnectionFailure(ex) =>
      log.error(s"Failed to establish HTTP connection $ex")
  }

  system.scheduler.scheduleWithFixedDelay(
    Duration.Zero,
    conf.mqtt.timeout.millis,
    self,
    Tick)
}

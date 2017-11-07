package mqtt

import akka.actor._
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import org.clapper.scalasti.ST
import org.eclipse.paho.client.mqttv3._
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import lib._

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.io.Source
import scala.util.{Failure, Success}

object Producer {
  def props()(implicit materializer: ActorMaterializer) =
    Props(classOf[Producer], materializer)

  case class SensorModel(name: String, isNormal: Boolean)

  private final case object Tick
}

class Producer()(implicit materializer: ActorMaterializer)
  extends Actor with ActorLogging {
  import Producer._

  implicit val system: ActorSystem = context.system
  implicit val executionContext: ExecutionContext = system.dispatcher

  private val conf = Config.get

  val factory = new EntryFactory(conf.mqtt.salt)
  val client = new MqttClient(conf.mqtt.broker,
    MqttClient.generateClientId,
    new MemoryPersistence
  )
  client.connect()
  private val msgTopic = client.getTopic(conf.mqtt.topic)

  private val sensors = conf.mqtt.sensors.asScala
  private var state = sensors.map(k => (k, "normal")).toMap

  private val route =
    path("update") {
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

  var httpBinding: Option[ServerBinding] = None
  val httpClient = new HttpClient(
    route,
    conf.producer.address,
    conf.producer.port,
    None,
    self
  )

  override def postStop():Unit = {
    httpBinding match {
      case Some(x) => x.unbind
      case None =>
    }
    client.disconnect()
  }

  override def receive: Receive = {
    case Tick =>
      val r = scala.util.Random
      val bound = conf.mqtt.bound

      for (sensor <- sensors) {
        val sensorState = state(sensor)
        val sign = if (r.nextBoolean()) -1 else 1
        val value = sign * (sensorState match {
          case "normal" => r.nextInt(bound)
          case "anomaly" => bound + r.nextInt(bound / 2)
        })
        val entry = factory.create(
          sensor,
          value,
          if (sensorState == "anomaly") 1 else 0
        )
        val token = msgTopic.publish(new MqttMessage(entry.toBytes))

        val messageId = token.getMessageId

        log.debug(s"Published message: id -> $messageId, payload -> $entry")
      }
    case Connected(binding) =>
      httpBinding = Some(binding)
    case ConnectionFailure(ex) =>
      log.error(s"Failed to establish HTTP connection $ex")
  }

  system.scheduler.schedule(0.millis, conf.mqtt.timeout.millis) {
    self ! Tick
  }
}

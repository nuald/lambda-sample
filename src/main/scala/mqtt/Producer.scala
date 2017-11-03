package mqtt

import akka.actor._
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer

import org.clapper.scalasti.ST
import org.eclipse.paho.client.mqttv3._
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.io.Source
import scala.util.{Failure, Success}

import lib._

object Producer {
  def props()(implicit materializer: ActorMaterializer) =
    Props(classOf[Producer], materializer)

  final case object Publish
}

class Producer()(implicit materializer: ActorMaterializer)
  extends Actor with ActorLogging {
  import Producer._

  implicit val system = context.system
  implicit val executionContext = system.dispatcher

  val conf = Config.get

  val id = MqttClient.generateClientId
  val persistence = new MemoryPersistence
  val factory = new EntryFactory(conf.mqtt.salt)
  val client = new MqttClient(conf.mqtt.broker, id, persistence)

  client.connect()
  val msgTopic = client.getTopic(conf.mqtt.topic)

  val r = scala.util.Random
  val bound = conf.mqtt.bound
  val offsetStep = bound

  val route =
    pathSingleSlash {
      get {
        val src = Source.fromFile("resources/producer/index.html").mkString
        val template = ST(src, '$', '$').add("sensors", conf.mqtt.sensors.asScala)
        template.render() match {
          case Success(dst) =>
            complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, dst))
          case Failure(ex) => complete(StatusCodes.InternalServerError, ex)
        }
      }
    }

  var httpBinding: Option[ServerBinding] = None
  val httpClient = HttpClient(
    route,
    conf.producer.address,
    conf.producer.port,
    None,
    self
  )

  override def postStop() = {
    httpBinding match {
      case Some(x) => x.unbind
      case None =>
    }
    client.disconnect
  }

  override def receive: Receive = {
    case Publish =>
      var offset = 0
      for (sensor <- conf.mqtt.sensors.asScala) {
        val value = offset + r.nextInt(bound)
        val entry = factory.create(sensor, value)
        val token = msgTopic.publish(new MqttMessage(entry.toBytes))

        val messageId = token.getMessageId()
        offset += offsetStep

        log.debug(s"Published message: id -> $messageId, payload -> $entry")
      }
    case Connected(binding) =>
      httpBinding = Some(binding)
    case ConnectionFailure(ex) =>
      log.error(s"Failed to establish HTTP connection $ex")
  }

  system.scheduler.schedule(0.millis, conf.mqtt.timeout.millis) {
    self ! Publish
  }
}

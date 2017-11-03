package mqtt

import akka.actor._
import akka.stream.ActorMaterializer

import org.eclipse.paho.client.mqttv3._
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

import scala.collection.JavaConverters._
import scala.concurrent.duration._

import lib._

object Producer {
  def props()(implicit materializer: ActorMaterializer) =
    Props(classOf[Producer], materializer)

  final case object Publish
}

class Producer()(implicit materializer: ActorMaterializer)
  extends Actor with ActorLogging {
  import Producer._

  implicit private val system = context.system
  implicit private val executionContext = system.dispatcher

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

  override def postStop() = {
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
  }

  system.scheduler.schedule(0.millis, conf.mqtt.timeout.millis) {
    self ! Publish
  }
}

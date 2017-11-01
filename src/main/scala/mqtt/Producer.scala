package mqtt

import lib._
import org.eclipse.paho.client.mqttv3._
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import scala.collection.JavaConverters._

object Producer extends App {
  val conf = Config.get
  implicit val logger = getLogger(conf.logger)

  val broker = conf.mqtt.broker
  val id = MqttClient.generateClientId
  val persistence = new MemoryPersistence
  val sensors = conf.mqtt.sensors
  val bound = conf.mqtt.bound
  val factory = new EntryFactory(conf.mqtt.salt)

  using(new MqttClient(broker, id, persistence))(_.disconnect) { client =>

    client.connect()
    val msgTopic = client.getTopic(conf.mqtt.topic)

    val r = scala.util.Random
    val offsetStep = bound
    while (true) {
      var offset = 0
      for (sensor <- sensors.asScala) {
        val value = offset + r.nextInt(bound)
        val entry = factory.create(sensor, value)
        val token = msgTopic.publish(new MqttMessage(entry.toBytes))

        val messageId = token.getMessageId()
        offset += offsetStep

        logger.debug(s"Published message: id -> $messageId, payload -> $entry")
      }
      Thread.sleep(conf.mqtt.timeout)
    }
  }
}

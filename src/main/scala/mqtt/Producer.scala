package mqtt

import lib._
import org.eclipse.paho.client.mqttv3._
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

object Producer extends App {
  val AmpUpperBound = 100
  val TimeoutUpperBound = 1000

  val conf = Config.get
  implicit val logger = getLogger(conf.logger)

  val broker = conf.mqtt.broker
  val id = MqttClient.generateClientId
  val persistence = new MemoryPersistence
  val sensors = conf.mqtt.sensors
  val factory = new EntryFactory(conf.mqtt.salt)

  using(new MqttClient(broker, id, persistence))(_.disconnect) { client =>

    client.connect()
    val msgTopic = client.getTopic(conf.mqtt.topic)

    val r = scala.util.Random
    while (true) {
      val value = r.nextInt(AmpUpperBound)
      val sensor = sensors.get(r.nextInt(sensors.size))
      val entry = factory.create(sensor, value)
      val token = msgTopic.publish(new MqttMessage(entry.toBytes))
      val messageId = token.getMessageId()

      logger.debug(s"Published message: id -> $messageId, payload -> $entry")
      Thread.sleep(r.nextInt(TimeoutUpperBound))
    }
  }
}

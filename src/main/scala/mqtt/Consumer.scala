package mqtt

import akka.actor._
import akka.event.LoggingAdapter
import akka.stream.ActorMaterializer
import org.eclipse.paho.client.mqttv3._
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.querybuilder.QueryBuilder._
import com.datastax.oss.driver.api.querybuilder.update._
import com.datastax.oss.driver.api.querybuilder.relation._
import lib._
import mqtt.Producer.MqttEntry

import scala.concurrent.ExecutionContext

object Consumer {
  def props(mqttClient: MqttClient, session: CqlSession)
           (implicit materializer: ActorMaterializer) =
    Props(classOf[Consumer], mqttClient, session, materializer)

  final case class Arrived(message: MqttMessage)
}

class Consumer(mqttClient: MqttClient, session: CqlSession)
              (implicit materializer: ActorMaterializer)
  extends Actor with ActorLogging {
  import Consumer._

  private[this] val conf = Config.get

  implicit val system: ActorSystem = context.system
  implicit val executionContext: ExecutionContext = system.dispatcher
  implicit val logger: LoggingAdapter = log

  mqttClient.subscribe(conf.mqtt.topic)

  mqttClient.setCallback(new MqttCallback {
    override def messageArrived(topic: String, message: MqttMessage): Unit = {
      self ! Arrived(message)
    }

    override def connectionLost(cause: Throwable): Unit = {
      log.info("Connection lost", cause)
    }

    override def deliveryComplete(token: IMqttDeliveryToken): Unit = {
    }
  })

  override def postStop(): Unit = {
    mqttClient.disconnect()
  }

  override def receive: Receive = {
    case Arrived(message) =>
      val serializer = new BinarySerializer()
      val entry = serializer.fromBinary(
        message.getPayload,
        BinarySerializer.MqttEntryManifest
      ).asInstanceOf[MqttEntry]

      val statement = update(conf.cassandra.table).set(
          Assignment.setColumn("value", literal(entry.value)),
          Assignment.setColumn("anomaly", literal(entry.anomaly)),
        ).where(
          Relation.column("sensor").isEqualTo(literal(entry.sensor)),
          Relation.column("ts").isEqualTo(literal(java.time.Instant.now()))
        ).build()
      session.execute(statement)
  }
}

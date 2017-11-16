package mqtt

import akka.actor._
import akka.event.LoggingAdapter
import akka.stream.ActorMaterializer
import org.eclipse.paho.client.mqttv3._
import com.datastax.driver.core.Cluster
import com.datastax.driver.core.querybuilder.QueryBuilder
import lib._
import mqtt.Producer.MqttEntry

import scala.concurrent.ExecutionContext

object Consumer {
  def props(mqttClient: MqttClient, cluster: Cluster)
           (implicit materializer: ActorMaterializer) =
    Props(classOf[Consumer], mqttClient, cluster, materializer)

  final case class Arrived(message: MqttMessage)
}

class Consumer(mqttClient: MqttClient, cluster: Cluster)
              (implicit materializer: ActorMaterializer)
  extends Actor with ActorLogging {
  import Consumer._

  implicit val system: ActorSystem = context.system
  implicit val executionContext: ExecutionContext = system.dispatcher
  implicit val logger: LoggingAdapter = log

  private val conf = Config.get
  private val session = cluster.connect(conf.cassandra.keyspace)

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
    session.close()
  }

  override def receive: Receive = {
    case Arrived(message) =>
      val serializer = new BinarySerializer()
      val entry = serializer.fromBinary(
        message.getPayload,
        BinarySerializer.MqttEntryManifest
      ).asInstanceOf[MqttEntry]

      val statement = QueryBuilder.update(conf.cassandra.table)
        .`with`(QueryBuilder.set("value", entry.value))
        .and(QueryBuilder.set("anomaly", entry.anomaly))
        .where(QueryBuilder.eq("sensor", entry.sensor))
        .and(QueryBuilder.eq("ts", System.currentTimeMillis))
      session.execute(statement)
  }
}

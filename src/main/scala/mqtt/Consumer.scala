package mqtt

import akka.actor._
import akka.stream.ActorMaterializer
import org.eclipse.paho.client.mqttv3._
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import com.datastax.driver.core.Cluster
import com.datastax.driver.core.querybuilder.QueryBuilder
import lib._

import scala.concurrent.ExecutionContext

object Consumer {
  def props(cluster: Cluster)(implicit materializer: ActorMaterializer) =
    Props(classOf[Consumer], cluster, materializer)

  final case class Arrived(message: MqttMessage)
}

class Consumer(cluster: Cluster)(implicit materializer: ActorMaterializer)
  extends Actor with ActorLogging {
  import Consumer._

  implicit val system: ActorSystem = context.system
  implicit val executionContext: ExecutionContext = system.dispatcher

  private val conf = Config.get
  private val session = cluster.connect(conf.cassandra.keyspace)

  val factory = new EntryFactory(conf.mqtt.salt)
  val client = new MqttClient(
    conf.mqtt.broker,
    MqttClient.generateClientId,
    new MemoryPersistence
  )
  client.connect()
  client.subscribe(conf.mqtt.topic)

  client.setCallback(new MqttCallback {
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
    client.disconnect()
    session.close()
  }

  override def receive: Receive = {
    case Arrived(message) =>
      log.debug(s"Message arrived: $message")
      try {
        val entry = factory.get(message.getPayload)

        val statement = QueryBuilder.update(conf.cassandra.table)
          .`with`(QueryBuilder.set("value", Integer.valueOf(entry.value)))
          .and(QueryBuilder.set("anomaly", entry.anomaly))
          .where(QueryBuilder.eq("sensor", entry.sensor))
          .and(QueryBuilder.eq("ts", System.currentTimeMillis))
        session.execute(statement)
      } catch {
        case e: Throwable => log.error(e, "Consumer error")
      }
  }
}

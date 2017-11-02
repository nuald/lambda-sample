package mqtt

import org.eclipse.paho.client.mqttv3._
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

import com.datastax.driver.core.Cluster
import com.datastax.driver.core.querybuilder.QueryBuilder

import scala.io.StdIn

import lib._

object Consumer extends App {
  val conf = Config.get
  implicit val logger = getLogger(conf.logger)

  val addr = conf.cassandra.address
  val keyspace = conf.cassandra.keyspace
  val table = conf.cassandra.table

  using(Cluster.builder().addContactPoint(addr).build())(_.close) { cluster =>
    using(cluster.connect(keyspace))(_.close) { session =>

      val broker = conf.mqtt.broker
      val id = MqttClient.generateClientId
      val persistence = new MemoryPersistence
      val factory = new EntryFactory(conf.mqtt.salt)

      using(new MqttClient(broker, id, persistence))(_.disconnect) { client =>

        client.connect()
        client.subscribe(conf.mqtt.topic)

        val callback = new MqttCallback {
          override def messageArrived(topic: String, message: MqttMessage): Unit = {
            logger.debug(s"Message arrived: $message")
            try {
              val entry = factory.get(message.getPayload)

              val statement = QueryBuilder.update(table)
                .`with`(QueryBuilder.set("value", Integer.valueOf(entry.value)))
                .where(QueryBuilder.eq("sensor", entry.sensor))
                .and(QueryBuilder.eq("ts", System.currentTimeMillis))
              session.execute(statement)
            } catch {
              case e: SecurityException => logger.warn("Consumer warning", e)
            }
          }

          override def connectionLost(cause: Throwable): Unit = {
            logger.info("Connection lost", cause)
          }

          override def deliveryComplete(token: IMqttDeliveryToken): Unit = {
          }
        }

        client.setCallback(callback)
        logger.info("Press <Enter> to exit")
        StdIn.readLine()
      }
    }
  }
}

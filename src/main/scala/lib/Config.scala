package lib

import akka.actor.ActorSystem

import com.typesafe.config.ConfigBeanFactory

import scala.beans.BeanProperty

class MqttConfig {
  @BeanProperty var broker = ""
  @BeanProperty var topic = ""
  @BeanProperty var salt = ""
  @BeanProperty var bound = 100
  @BeanProperty var timeout = 200
  @BeanProperty var sensors: java.util.List[String] = new java.util.ArrayList[String]()
}

class CassandraConfig {
  @BeanProperty var address = ""
  @BeanProperty var keyspace = ""
  @BeanProperty var table = ""
}

class DashboardConfig {
  @BeanProperty var address = ""
  @BeanProperty var port = 8080
  @BeanProperty var limit = 100
}

class ProducerConfig {
  @BeanProperty var address = ""
  @BeanProperty var port = 8081
}

class Config {
  @BeanProperty var mqtt = new MqttConfig
  @BeanProperty var cassandra = new CassandraConfig
  @BeanProperty var dashboard = new DashboardConfig
  @BeanProperty var producer = new ProducerConfig
}

object Config {
  def get()(implicit system: ActorSystem): Config = {
    val config = system.settings.config
    ConfigBeanFactory.create(config.getConfig("config"), classOf[Config])
  }
}

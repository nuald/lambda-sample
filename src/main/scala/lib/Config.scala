package lib

import akka.actor.ActorSystem

import com.typesafe.config.ConfigBeanFactory

import scala.beans.BeanProperty

class MqttConfig {
  @BeanProperty var broker: String = ""
  @BeanProperty var topic: String = ""
  @BeanProperty var salt: String = ""
  @BeanProperty var bound: Int = 100
  @BeanProperty var timeout: Int = 200
  @BeanProperty var sensors: java.util.List[String] = new java.util.ArrayList[String]()
}

class CassandraConfig {
  @BeanProperty var address: String = ""
  @BeanProperty var keyspace: String = ""
  @BeanProperty var table: String = ""
}

class DashboardConfig {
  @BeanProperty var address: String = ""
  @BeanProperty var port: Int = 8080
  @BeanProperty var limit: Int = 100
}

class Config {
  @BeanProperty var mqtt: MqttConfig = new MqttConfig
  @BeanProperty var cassandra: CassandraConfig = new CassandraConfig
  @BeanProperty var dashboard: DashboardConfig = new DashboardConfig
}

object Config {
  def get()(implicit system: ActorSystem): Config = {
    val config = system.settings.config
    ConfigBeanFactory.create(config.getConfig("config"), classOf[Config])
  }
}

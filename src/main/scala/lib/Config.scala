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
  @BeanProperty var recent = 100
  @BeanProperty var full = 10000
  @BeanProperty var table = ""
}

class RedisConfig {
  @BeanProperty var address = ""
  @BeanProperty var port = 6379
  @BeanProperty var salt = ""
}

class DashboardConfig {
  @BeanProperty var address = ""
  @BeanProperty var port = 8080
  @BeanProperty var timeout = 5000
}

class ProducerConfig {
  @BeanProperty var address = ""
  @BeanProperty var port = 8081
}

class EndpointConfig {
  @BeanProperty var address = ""
  @BeanProperty var port = 8082
  @BeanProperty var timeout = 1000
}

class FastAnalyzerConfig {
  @BeanProperty var timeout = 1000
  @BeanProperty var key = ""
}

class FullAnalyzerConfig {
  @BeanProperty var timeout = 1000
  @BeanProperty var period = 2000
  @BeanProperty var key = ""
}

class HistoryWriterConfig {
  @BeanProperty var period = 1000
  @BeanProperty var table = ""
}

class Config {
  @BeanProperty var mqtt = new MqttConfig
  @BeanProperty var cassandra = new CassandraConfig
  @BeanProperty var redis = new RedisConfig
  @BeanProperty var dashboard = new DashboardConfig
  @BeanProperty var producer = new ProducerConfig
  @BeanProperty var endpoint = new EndpointConfig
  @BeanProperty var fastAnalyzer = new FastAnalyzerConfig
  @BeanProperty var fullAnalyzer = new FullAnalyzerConfig
  @BeanProperty var historyWriter = new HistoryWriterConfig
}

object Config {
  def get()(implicit system: ActorSystem): Config = {
    val config = system.settings.config
    ConfigBeanFactory.create(config.getConfig("config"), classOf[Config])
  }
}

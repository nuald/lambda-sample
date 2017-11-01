package lib

import ammonite.ops._
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import scala.beans.BeanProperty
import scala.util._

class MqttConfig {
  @BeanProperty var broker = ""
  @BeanProperty var topic = ""
  @BeanProperty var salt = ""
  @BeanProperty var bound = 100
  @BeanProperty var timeout = 200
  @BeanProperty var sensors = new java.util.ArrayList[String]()
}

class CassandraConfig {
  @BeanProperty var address = ""
  @BeanProperty var keyspace = ""
  @BeanProperty var table = ""
}

class LoggerConfig {
  @BeanProperty var name = ""
  @BeanProperty var debug = false
}

class DashboardConfig {
  @BeanProperty var address = ""
  @BeanProperty var port = 8080
  @BeanProperty var limit = 100
}

class Config {
  @BeanProperty var mqtt = new MqttConfig
  @BeanProperty var cassandra = new CassandraConfig
  @BeanProperty var logger = new LoggerConfig
  @BeanProperty var dashboard = new DashboardConfig
}

object Config {
  def get: Config = {
    val file = pwd / 'resources / "config.yaml"
    val yaml = new Yaml(new Constructor(classOf[Config]))
    yaml.load(read! file).asInstanceOf[Config]
  }
}

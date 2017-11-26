package lib

import analyzer.SensorMeta
import com.datastax.driver.core.querybuilder.QueryBuilder
import com.datastax.driver.core.{Cluster, ResultSet}

import scala.collection.JavaConverters._

final case class Entry(sensor: String, ts: java.util.Date, value: Double, anomaly: Int)

class CassandraClient(cluster: Cluster) {
  private val conf = Config.get
  private val session = cluster.connect(conf.cassandra.keyspace)

  def values(sensor: String, table: String, limit: Int): ResultSet = {
    val query = QueryBuilder.select().all()
      .from(table)
      .where(QueryBuilder.eq("sensor", sensor))
      .limit(limit)
    session.execute(query)
  }

  def getEntries(rs: ResultSet): Iterable[Entry] = {
    for (row <- rs.asScala)
      yield Entry(
        row.getString("sensor"),
        row.getTimestamp("ts"),
        row.getDouble("value"),
        row.getInt("anomaly")
      )
  }

  def getMeta(rs: ResultSet): Iterable[SensorMeta] = {
    for (row <- rs.asScala)
      yield SensorMeta(
        row.getString("sensor"),
        row.getTimestamp("ts"),
        row.getDouble("fast_anomaly"),
        row.getDouble("full_anomaly"),
        row.getDouble("avg_anomaly")
      )
  }

  def close(): Unit = {
    session.close()
  }

  def recentAll(): Iterable[Entry] = {
    val entries =
      for (sensor <- conf.mqtt.sensors.asScala)
        yield getEntries(values(sensor, conf.cassandra.table, conf.cassandra.recent))
    entries.flatten
  }

  def recent(sensor: String): Iterable[Entry] = {
    getEntries(values(sensor, conf.cassandra.table, conf.cassandra.recent))
  }

  def full(sensor: String): Iterable[Entry] = {
    getEntries(values(sensor, conf.cassandra.table, conf.cassandra.full))
  }

  def historyAll(): Iterable[SensorMeta] = {
    val entries =
      for (sensor <- conf.mqtt.sensors.asScala)
        yield getMeta(values(sensor, conf.historyWriter.table, conf.cassandra.recent))
    entries.flatten
  }
}

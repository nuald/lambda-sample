package lib

import analyzer.SensorMeta
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.ResultSet
import com.datastax.oss.driver.api.querybuilder.QueryBuilder._
import com.datastax.oss.driver.api.querybuilder.relation._

import scala.jdk.CollectionConverters._

final case class Entry(sensor: String, ts: java.time.Instant, value: Double, anomaly: Int)

class CassandraClient(session: CqlSession) {
  private[this] val conf = Config.get

  def recentAll(): Iterable[Entry] = {
    val entries =
      for (sensor <- conf.mqtt.sensorsList)
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
      for (sensor <- conf.mqtt.sensorsList)
        yield getMeta(values(sensor, conf.historyWriter.table, conf.cassandra.recent))
    entries.flatten
  }

  private[this] def values(sensor: String, table: String, limit: Int): ResultSet = {
    val query = selectFrom(table).all()
      .where(Relation.column("sensor").isEqualTo(literal(sensor)))
      .limit(limit).build()
    session.execute(query)
  }

  private[this] def getEntries(rs: ResultSet): Iterable[Entry] = {
    for (row <- rs.asScala)
      yield Entry(
        row.getString("sensor"),
        row.getInstant("ts"),
        row.getDouble("value"),
        row.getInt("anomaly")
      )
  }

  private[this] def getMeta(rs: ResultSet): Iterable[SensorMeta] = {
    for (row <- rs.asScala)
      yield SensorMeta(
        row.getString("sensor"),
        row.getInstant("ts"),
        row.getDouble("fast_anomaly"),
        row.getDouble("full_anomaly"),
        row.getDouble("avg_anomaly")
      )
  }
}

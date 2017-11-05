package lib

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.stream.ActorMaterializer
import analyzer.SensorMeta
import com.datastax.driver.core.{Cluster, ResultSet}
import com.datastax.driver.core.querybuilder.QueryBuilder

import scala.concurrent.ExecutionContext
import scala.collection.JavaConverters._

object CassandraClient {
  def props(cluster: Cluster)(implicit materializer: ActorMaterializer) =
    Props(classOf[CassandraClient], cluster, materializer)

  final case class Entry(sensor: String, ts: java.util.Date, value: Int)
  final case class Recent(sensor: String)

  final case object RecentAll
  final case object HistoryAll
}

class CassandraClient(cluster: Cluster)(implicit materializer: ActorMaterializer)
  extends Actor with ActorLogging {

  import CassandraClient._

  implicit val system: ActorSystem = context.system
  implicit val executionContext: ExecutionContext = system.dispatcher

  private val conf = Config.get
  private val session = cluster.connect(conf.cassandra.keyspace)

  def values(sensor: String, table: String): ResultSet = {
    val query = QueryBuilder.select().all()
      .from(table)
      .where(QueryBuilder.eq("sensor", sensor))
      .limit(conf.cassandra.recent)
    session.execute(query)
  }

  def getEntries(rs: ResultSet): Iterable[Entry] = {
    for (row <- rs.asScala)
      yield Entry(
        row.getString("sensor"),
        row.getTimestamp("ts"),
        row.getInt("value")
      )
  }

  def getMeta(rs: ResultSet): Iterable[SensorMeta] = {
    for (row <- rs.asScala)
      yield SensorMeta(
        row.getString("sensor"),
        row.getTimestamp("ts"),
        row.getDouble("anomaly")
      )
  }

  override def postStop(): Unit = {
    session.close()
  }

  override def receive: Receive = {
    case RecentAll =>
      val entries =
        for (sensor <- conf.mqtt.sensors.asScala)
          yield getEntries(values(sensor, conf.cassandra.table))
      sender() ! entries.flatten

    case Recent(sensor) =>
      sender() ! getEntries(values(sensor, conf.cassandra.table))

    case HistoryAll =>
      val entries =
        for (sensor <- conf.mqtt.sensors.asScala)
          yield getMeta(values(sensor, conf.historyWriter.table))
      sender() ! entries.flatten
  }
}

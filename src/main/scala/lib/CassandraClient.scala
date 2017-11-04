package lib

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.stream.ActorMaterializer
import com.datastax.driver.core.Cluster
import com.datastax.driver.core.querybuilder.QueryBuilder

import scala.concurrent.ExecutionContext
import scala.collection.JavaConverters._

object CassandraClient {
  def props(cluster: Cluster)(implicit materializer: ActorMaterializer) =
    Props(classOf[CassandraClient], cluster, materializer)

  final case class Entry(sensor: String, ts: java.util.Date, value: Int)
  final case class Recent(sensor: String)

  final case object RecentAll
}

class CassandraClient(cluster: Cluster)(implicit materializer: ActorMaterializer)
  extends Actor with ActorLogging {

  import CassandraClient._

  implicit val system: ActorSystem = context.system
  implicit val executionContext: ExecutionContext = system.dispatcher

  private val conf = Config.get
  private val session = cluster.connect(conf.cassandra.keyspace)

  def values(sensor: String): Iterable[Entry] = {
    val query = QueryBuilder.select().all()
      .from(conf.cassandra.table)
      .where(QueryBuilder.eq("sensor", sensor))
      .limit(conf.cassandra.recent)
    val rs = session.execute(query)

    for (row <- rs.asScala)
      yield Entry(
        row.getString("sensor"),
        row.getTimestamp("ts"),
        row.getInt("value")
      )
  }

  override def postStop(): Unit = {
    session.close()
  }

  override def receive: Receive = {
    case RecentAll =>
      val entries =
        for (sensor <- conf.mqtt.sensors.asScala) yield values(sensor)
      sender() ! entries.flatten

    case Recent(sensor) =>
      sender() ! values(sensor)
  }
}

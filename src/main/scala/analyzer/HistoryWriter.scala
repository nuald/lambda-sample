package analyzer

import akka.actor._
import akka.stream.ActorMaterializer
import analyzer.Endpoint.Analyze
import com.datastax.driver.core.Cluster
import com.datastax.driver.core.querybuilder.QueryBuilder
import com.redis.RedisClient
import com.redis.serialization.Parse.Implicits._
import lib._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.collection.JavaConverters._
import scala.util.{Failure, Success}

object HistoryWriter {
  def props(cluster: Cluster, fastAnalyzer: ActorRef)
           (implicit materializer: ActorMaterializer) =
    Props(classOf[HistoryWriter], cluster, fastAnalyzer, materializer)

  private final case object TickKey
  private final case object Tick
}

class HistoryWriter(cluster: Cluster, fastAnalyzer: ActorRef)
                   (implicit materializer: ActorMaterializer)
  extends Actor with ActorLogging {
  import HistoryWriter._

  implicit val system: ActorSystem = context.system
  implicit val executionContext: ExecutionContext = system.dispatcher

  private val conf = Config.get
  private val session = cluster.connect(conf.cassandra.keyspace)

  private val lastTimestamp = collection.mutable.Map(
    conf.mqtt.sensors.asScala.map(a => (a, new java.util.Date())): _*
  )

  override def postStop(): Unit = {
    session.close()
  }

  override def receive: Receive = {
    case Tick =>
      val forceAnalyze:Seq[Future[Boolean]] = for (sensor <- conf.mqtt.sensors.asScala)
        yield Future {
          val r = new RedisClient(conf.redis.address, conf.redis.port)
          val bytes = r.hget[Array[Byte]](conf.fastAnalyzer.key, sensor)
          val needAnalyzeSensor = bytes match {
            case Some(b) =>
              val meta = SensorMeta.get(b)
              val notUpdatedYet = lastTimestamp(sensor) == meta.ts
              val statement = QueryBuilder.update(conf.historyWriter.table)
                .`with`(QueryBuilder.set("anomaly", meta.anomaly))
                .where(QueryBuilder.eq("sensor", meta.name))
                .and(QueryBuilder.eq("ts", meta.ts))
              session.execute(statement)
              lastTimestamp(sensor) = meta.ts
              notUpdatedYet
            case None =>
              true
          }
          needAnalyzeSensor
        }

      Future.sequence(forceAnalyze).onComplete {
        case Success(force) => if (force.exists(x => x)) {
          fastAnalyzer ! Analyze
        }
        case Failure(t) => log.error("History writer error {}", t)
      }
  }

  system.scheduler.schedule(0.millis, conf.historyWriter.period.millis) {
    self ! Tick
  }
}

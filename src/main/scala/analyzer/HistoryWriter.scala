package analyzer

import akka.actor._
import akka.event.LoggingAdapter
import akka.stream.ActorMaterializer
import analyzer.Endpoint.Analyze
import com.datastax.driver.core.Cluster
import com.datastax.driver.core.querybuilder.QueryBuilder
import lib._
import redis.RedisClient

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.collection.JavaConverters._
import scala.util.{Failure, Success}

object HistoryWriter {
  def props(cluster: Cluster, fastAnalyzer: ActorRef)
           (implicit materializer: ActorMaterializer) =
    Props(classOf[HistoryWriter], cluster, fastAnalyzer, materializer)

  private final case object Tick
}

class HistoryWriter(cluster: Cluster, fastAnalyzer: ActorRef)
                   (implicit materializer: ActorMaterializer)
  extends Actor with ActorLogging {
  import HistoryWriter._

  implicit val system: ActorSystem = context.system
  implicit val executionContext: ExecutionContext = system.dispatcher
  implicit val logger: LoggingAdapter = log

  private val conf = Config.get
  val seal = new Sealed[SensorMeta](conf.mqtt.salt)
  val r = RedisClient(conf.redis.address, conf.redis.port)
  private val session = cluster.connect(conf.cassandra.keyspace)

  private val lastTimestamp = collection.mutable.Map(
    conf.mqtt.sensors.asScala.map(a => (a, new java.util.Date())): _*
  )

  override def postStop(): Unit = {
    session.close()
  }

  override def receive: Receive = {
    case Tick =>
      val forceAnalyze =
        for (sensor <- conf.mqtt.sensors.asScala)
          yield needUpdate(sensor)

      Future.sequence(forceAnalyze).onComplete {
        case Success(force) =>
          if (force.exists(x => x)) {
            fastAnalyzer ! Analyze
          }
        case Failure(t) => log.error("History writer error {}", t)
      }
  }

  def needUpdate(sensor: String): Future[Boolean] = for {
    bytesOpt <- r.hget(conf.fastAnalyzer.key, sensor)
  } yield {
    val force = bytesOpt map { bytes =>
      seal.fromBytes(bytes.toArray).toOption map { meta =>
        val notUpdatedYet = lastTimestamp(sensor) == meta.ts
        val statement = QueryBuilder.update(conf.historyWriter.table)
          .`with`(QueryBuilder.set("anomaly", meta.anomaly))
          .where(QueryBuilder.eq("sensor", meta.name))
          .and(QueryBuilder.eq("ts", meta.ts))
        session.execute(statement)
        lastTimestamp(sensor) = meta.ts
        notUpdatedYet
      }
    }
    force.flatten.getOrElse(true)
  }

  system.scheduler.schedule(0.millis, conf.historyWriter.period.millis) {
    self ! Tick
  }
}

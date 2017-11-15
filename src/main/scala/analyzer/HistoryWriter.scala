package analyzer

import akka.actor._
import akka.event.LoggingAdapter
import akka.stream.ActorMaterializer
import analyzer.Analyzer.Registration
import com.datastax.driver.core.Cluster
import com.datastax.driver.core.querybuilder.QueryBuilder
import lib._
import redis.RedisClient

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.collection.JavaConverters._
import scala.util.{Failure, Success}

object HistoryWriter {
  def props(cluster: Cluster, redisClient: RedisClient, analyzer: ActorRef)
           (implicit materializer: ActorMaterializer) =
    Props(classOf[HistoryWriter], cluster, redisClient, analyzer, materializer)

  private final case object Tick
}

class HistoryWriter(cluster: Cluster, redisClient: RedisClient, analyzer: ActorRef)
                   (implicit materializer: ActorMaterializer)
  extends Actor with ActorLogging {
  import HistoryWriter._

  implicit val system: ActorSystem = context.system
  implicit val executionContext: ExecutionContext = system.dispatcher
  implicit val logger: LoggingAdapter = log

  private val conf = Config.get
  private val sealReader = new Sealed[SensorMeta](conf.redis.salt).reader
  private val session = cluster.connect(conf.cassandra.keyspace)

  private val lastTimestamp = collection.mutable.Map(
    conf.mqtt.sensors.asScala.map(a => (a, new java.util.Date())): _*
  )

  private var analyzers = IndexedSeq(analyzer)
  var jobCounter = 0

  override def postStop(): Unit = {
    session.close()
  }

  override def receive: Receive = {
    case Tick =>
      val doNeedUpdate =
        for (sensor <- conf.mqtt.sensors.asScala)
          yield needUpdate(sensor)

      Future.sequence(doNeedUpdate).onComplete {
        case Success(force) =>
          if (force.exists(x => x)) {
            forceAnalyze()
          }
        case Failure(t) =>
          log.error("History writer error {}", t)
          forceAnalyze()
      }

    case Registration if !analyzers.contains(sender()) =>
      context watch sender()
      analyzers = analyzers :+ sender()

    case Terminated(a) =>
      analyzers = analyzers.filterNot(_ == a)
  }

  def forceAnalyze(): Unit = {
    if (analyzers.nonEmpty) {
      jobCounter += 1
      val analyzer = analyzers(jobCounter % analyzers.size)
      analyzer ! Analyze
    }
  }

  def needUpdate(sensor: String): Future[Boolean] =
    for {
      bytesOpt <- redisClient.hget(conf.fastAnalyzer.key, sensor)
    } yield {
      val force = bytesOpt map { bytes =>
        sealReader(bytes.toArray).toOption map { meta =>
          val notUpdatedYet = lastTimestamp(sensor) == meta.ts
          val statement = QueryBuilder.update(conf.historyWriter.table)
            .`with`(QueryBuilder.set("fast_anomaly", meta.fastAnomaly))
            .and(QueryBuilder.set("full_anomaly", meta.fullAnomaly))
            .and(QueryBuilder.set("avg_anomaly", meta.avgAnomaly))
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

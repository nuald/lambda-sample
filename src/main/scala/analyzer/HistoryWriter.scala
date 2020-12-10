package analyzer

import akka.actor._
import akka.pattern.ask
import akka.event.LoggingAdapter
import akka.util.Timeout
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.querybuilder.QueryBuilder._
import com.datastax.oss.driver.api.querybuilder.update._
import com.datastax.oss.driver.api.querybuilder.relation._
import lib._
import redis.RedisClient

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object HistoryWriter {
  def props(session: CqlSession, redisClient: RedisClient, analyzerOpt: Option[ActorRef]) =
    Props(classOf[HistoryWriter], session, redisClient, analyzerOpt)

  private final case object Tick
}

class HistoryWriter(session: CqlSession, redisClient: RedisClient, analyzerOpt: Option[ActorRef])
  extends Actor with ActorLogging {
  import HistoryWriter._

  private[this] val conf = Config.get
  private[this] var analyzers = analyzerOpt.toIndexedSeq
  private[this] var jobCounter = 0
  private[this] val lastTimestamp = mutable.Map[String, java.time.Instant]()

  implicit val system: ActorSystem = context.system
  implicit val executionContext: ExecutionContext = system.dispatcher
  implicit val logger: LoggingAdapter = log
  implicit val timeout: Timeout = Timeout(conf.historyWriter.timeout.millis)

  override def receive: Receive = {
    case Tick =>
      val doNeedUpdate =
        for (sensor <- conf.mqtt.sensorsList)
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

  private[this] def forceAnalyze(): Unit = {
    if (analyzers.nonEmpty) {
      jobCounter += 1
      val analyzer = analyzers(jobCounter % analyzers.size)
      val serializer = new BinarySerializer()
      ask(analyzer, Analyze).mapTo[AllMeta] foreach { x =>
        for (meta <- x.entries) {
          val bytes = serializer.toBinary(meta)
          redisClient.hset(conf.fastAnalyzer.key, meta.name, bytes)
        }
      }
    }
  }

  private[this] def needUpdate(sensor: String): Future[Boolean] = {
    val serializer = new BinarySerializer()
    for {
      bytesOpt <- redisClient.hget(conf.fastAnalyzer.key, sensor)
    } yield {
      val force = bytesOpt map { bytes =>
        val meta = serializer.fromBinary(
          bytes.toArray,
          BinarySerializer.SensorMetaManifest
        ).asInstanceOf[SensorMeta]

        val notUpdatedYet = lastTimestamp.contains(sensor) && lastTimestamp(sensor) == meta.ts
        val statement = update(conf.historyWriter.table).set(
          Assignment.setColumn("fast_anomaly", literal(meta.fastAnomaly)),
          Assignment.setColumn("full_anomaly", literal(meta.fullAnomaly)),
          Assignment.setColumn("avg_anomaly", literal(meta.avgAnomaly))
        ).where(
          Relation.column("sensor").isEqualTo(literal(meta.name)),
          Relation.column("ts").isEqualTo(literal(meta.ts))
        ).build()
        session.execute(statement)

        lastTimestamp(sensor) = meta.ts
        notUpdatedYet
      }
      force.getOrElse(true)
    }
  }

  system.scheduler.scheduleWithFixedDelay(
    Duration.Zero,
    conf.historyWriter.period.millis,
    self,
    Tick)
}

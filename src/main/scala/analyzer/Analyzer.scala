package analyzer

import java.util.Date

import akka.actor.{Actor, ActorLogging, ActorSystem, Props, RootActorPath}
import akka.cluster.{Cluster, Member, MemberStatus}
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberUp}
import akka.event.LoggingAdapter
import akka.pattern.pipe
import akka.stream.ActorMaterializer
import akka.util.Timeout
import lib.{BinarySerializer, CassandraClient, Config, Entry}
import redis.RedisClient
import smile.classification.RandomForest

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

case object Analyze
case object StressAnalyze
case object Registration

final case class SensorMeta(
  name: String,
  ts: java.util.Date,
  fastAnomaly: Double,
  fullAnomaly: Double,
  avgAnomaly: Double
)

final case class AllMeta(entries: List[SensorMeta])

object Analyzer {
  def props(cassandraClient: CassandraClient, redisClient: RedisClient)
           (implicit materializer: ActorMaterializer) =
    Props(classOf[Analyzer], cassandraClient, redisClient, materializer)

  // ANCHOR: withHeuristic begin

  /**
    * Calculates the probability of the anomaly with the heuristics.
    *
    * @param value The analyzed value
    * @param history The previous values
    * @return The probability of the anomaly
    */
  def withHeuristic(value: Double, history: Iterable[Double]): Double = {
    val size = history.size
    val avg = history.sum / size

    def sqrDiff(x: Double) = (x - avg) * (x - avg)
    val stdDev = math.sqrt((0.0 /: history)(_ + sqrDiff(_)) / size)

    val valueDev = math.abs(value - avg)
    val anomaly = (valueDev - stdDev) / (2 * stdDev)

    // truncate the value to be in the [0, 1] interval
    anomaly.max(0).min(1)
  }

  // ANCHOR: withHeuristic end

  // ANCHOR: withTrainedModel begin

  /**
    * Calculates the probability of the anomaly with the trained model.
    *
    * @param value The analyzed value
    * @param rf The trained model (Random Forest classification)
    * @return The probability of the anomaly
    */
  def withTrainedModel(value: Double, rf: RandomForest): Double = {
    val probability = ML.RandomForest(rf).predict(Array(value))

    // anomaly class has the index 1
    probability(1)
  }

  // ANCHOR: withTrainedModel end
}

class Analyzer(cassandraClient: CassandraClient, redisClient: RedisClient)
              (implicit materializer: ActorMaterializer)
  extends Actor with ActorLogging {

  private[this] val akkaCluster = Cluster(context.system)
  private[this] val conf = Config.get
  private[this] var lastMeta: Option[AllMeta] = None

  implicit val system: ActorSystem = context.system
  implicit val executionContext: ExecutionContext = system.dispatcher
  implicit val logger: LoggingAdapter = log
  implicit val timeout: Timeout = Timeout(conf.fastAnalyzer.timeout.millis)

  override def preStart(): Unit = akkaCluster.subscribe(self, classOf[MemberUp])
  override def postStop(): Unit = akkaCluster.unsubscribe(self)

  override def receive: Receive = {
    case Analyze =>
      val futures: Seq[Future[SensorMeta]] =
        for (sensor <- conf.mqtt.sensorsList)
          yield for {
            rf <- fetchModel(sensor)
          } yield analyze(sensor, cassandraClient.recent(sensor), rf)

      Future.sequence(futures) map {x =>
        val meta = AllMeta(x.toList)
        lastMeta = Some(meta)
        meta
      } pipeTo sender()

    case StressAnalyze =>
      lastMeta match {
        case Some(x) => sender() ! x
        case None => self forward Analyze
      }
    case state: CurrentClusterState =>
      state.members.filter(_.status == MemberStatus.Up) foreach register

    case MemberUp(m) => register(m)
  }

  // ANCHOR: analyze begin

  /**
    * Calculates the probability of the anomaly using both heuristics and trained model
    *
    * @param name Name of the IoT sensor
    * @param entries Sensor history entries
    * @param rf Optionally trained model
    * @return Meta information with the results of the analysis
    */
  private def analyze(name: String, entries: Iterable[Entry], rf: Option[RandomForest]) = {
    val values = entries.map(_.value)
    val value = values.head

    val approxAnomaly = Analyzer.withHeuristic(value, values)
    val mlAnomalyOpt = rf.map(Analyzer.withTrainedModel(value, _))
    val avgAnomaly = mlAnomalyOpt match {
      case Some(mlAnomaly) => (35.0 * approxAnomaly + 65.0 * mlAnomaly) / 100.0
      case None => approxAnomaly
    }

    val ts = new Date(System.currentTimeMillis)
    SensorMeta(name, ts, approxAnomaly, mlAnomalyOpt.getOrElse(-1), avgAnomaly)
  }

  // ANCHOR: analyze end

  private[this] def fetchModel(sensor: String): Future[Option[RandomForest]] = {
    val serializer = new BinarySerializer()
    for {
      bytesOpt <- redisClient.hget(conf.fullAnalyzer.key, sensor)
    } yield bytesOpt map { bytes =>
      serializer.fromBinary(
        bytes.toArray,
        BinarySerializer.RandomForestManifest
      ).asInstanceOf[RandomForest]
    }
  }

  private[this] def register(member: Member): Unit = {
    if (member.hasRole("frontend")) {
      context.actorSelection(RootActorPath(member.address) / "user" / "endpoint") !
        Registration
      context.actorSelection(RootActorPath(member.address) / "user" / "history-writer") !
        Registration
    }
  }
}

package analyzer

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

  def getAnomalyFast(value: Double, values: Iterable[Double]): Double = {
    val size = values.size
    val avg = values.sum / size
    val stddev = math.sqrt(
      values.map(x => math.pow(x - avg, 2)).sum / size
    )
    val recentDev = math.abs(value - avg)
    val anomaly = (recentDev - stddev) / (2 * stddev)
    if (anomaly < 0) {
      0
    } else if (anomaly > 1) {
      1
    } else {
      anomaly
    }
  }

  def getAnomalyFull(value: Double, rf: RandomForest): Double = {
    val probability = new Array[Double](2)
    val prediction = rf.predict(Array(value), probability)
    if (prediction == 1) {
      probability.max
    } else {
      probability.min
    }
  }
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

  private[this] def analyze(sensor: String, entries: Iterable[Entry], rfOpt: Option[RandomForest]): SensorMeta = {
    val values = entries.map(_.value)
    val value = values.head
    val fastAnomaly = Analyzer.getAnomalyFast(value, values)
    val fullAnomalyOpt = rfOpt map { rf =>
      Analyzer.getAnomalyFull(value, rf)
    }
    val avgAnomaly = fullAnomalyOpt match {
      case Some(fullAnomaly) => (35.0 * fastAnomaly + 65.0 * fullAnomaly) / 100.0
      case None => fastAnomaly
    }
    SensorMeta(
      sensor,
      new java.util.Date(System.currentTimeMillis),
      fastAnomaly,
      fullAnomalyOpt.getOrElse(-1),
      avgAnomaly
    )
  }

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

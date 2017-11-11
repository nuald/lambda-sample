package analyzer

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, RootActorPath}
import akka.cluster.{Cluster, Member, MemberStatus}
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberUp}
import akka.event.LoggingAdapter
import akka.pattern.{ask, pipe}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import analyzer.Endpoint.Analyze
import lib.CassandraClient.{Entry, Recent}
import lib.{Config, Sealed}
import redis.RedisClient
import smile.classification.RandomForest

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.collection.JavaConverters._

final case class SensorMeta(
  name: String,
  ts: java.util.Date,
  fastAnomaly: Double,
  fullAnomaly: Double,
  avgAnomaly: Double
) extends Serializable

object Analyzer {
  def props(cassandraClient: ActorRef, redisClient: RedisClient)(implicit materializer: ActorMaterializer) =
    Props(classOf[Analyzer], cassandraClient, redisClient, materializer)

  def getAnomalyFast(value: Double, values: List[Double]): Double = {
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

  final case object Registration
}

class Analyzer(cassandraClient: ActorRef, redisClient: RedisClient)(implicit materializer: ActorMaterializer)
  extends Actor with ActorLogging {
  import Analyzer._

  implicit val system: ActorSystem = context.system
  implicit val executionContext: ExecutionContext = system.dispatcher
  implicit val logger: LoggingAdapter = log

  val cluster = Cluster(context.system)

  private val conf = Config.get
  private val metaWriter = new Sealed[SensorMeta](conf.redis.salt).writer
  private val rfReader = new Sealed[RandomForest](conf.redis.salt).reader
  implicit val timeout: Timeout = Timeout(conf.fastAnalyzer.timeout.millis)

  override def preStart(): Unit = cluster.subscribe(self, classOf[MemberUp])
  override def postStop(): Unit = cluster.unsubscribe(self)

  def analyze(sensor: String, entries: List[Entry], rfOpt: Option[RandomForest]): SensorMeta = {
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

  def fetchModel(sensor: String): Future[Option[RandomForest]] =
    for {
      bytesOpt <- redisClient.hget(conf.fullAnalyzer.key, sensor)
    } yield {
      val rfOpt = bytesOpt map { bytes =>
        rfReader(bytes.toArray).toOption
      }
      rfOpt.flatten
    }

  override def receive: Receive = {
    case Analyze =>
      val futures =
        for (sensor <- conf.mqtt.sensors.asScala)
          yield for {
            entries <- ask(cassandraClient, Recent(sensor)).mapTo[List[Entry]]
            rf <- fetchModel(sensor)
          } yield {
            val meta = analyze(sensor, entries, rf)
            metaWriter(meta) foreach { bytes =>
              redisClient.hset(conf.fastAnalyzer.key, sensor, bytes)
            }
            meta
          }

      Future.sequence(futures) pipeTo sender()

    case state: CurrentClusterState =>
      state.members.filter(_.status == MemberStatus.Up) foreach register

    case MemberUp(m) => register(m)
  }

  def register(member: Member): Unit = {
    if (member.hasRole("frontend")) {
      context.actorSelection(RootActorPath(member.address) / "user" / "endpoint") !
        Registration
      context.actorSelection(RootActorPath(member.address) / "user" / "history-writer") !
        Registration
    }
  }
}

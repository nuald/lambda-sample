package dashboard

import akka.actor._
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import analyzer.Endpoint.Stats
import lib._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.sys.process._

object Dashboard {
  def props(cassandraClient: CassandraClient, endpoint: ActorRef)
           (implicit materializer: ActorMaterializer) =
    Props(classOf[Dashboard], cassandraClient, endpoint, materializer)

  final case class Perf(timings: List[Double], actorStats: Map[String, Double])
}

class Dashboard(cassandraClient: CassandraClient, endpoint: ActorRef)
               (implicit materializer: ActorMaterializer)
  extends Actor with ActorLogging {
  import Dashboard._

  implicit val system: ActorSystem = context.system
  implicit val executionContext: ExecutionContext = system.dispatcher

  private val conf = Config.get
  implicit val timeout: Timeout = Timeout(conf.dashboard.timeout.millis)

  val serializer = new JsonSerializer()

  var httpBinding: Option[ServerBinding] = None
  var httpClient: Option[HttpClient] = None

  def getPerf: Future[Perf] =
    runHey flatMap (timings =>
      ask(endpoint, Stats).mapTo[Map[String, Double]] map (Perf(timings, _))
    )

  private val CsvPattern = raw"""([\d\.]+),([\d\.]+),([\d\.]+),([\d\.]+),([\d\.]+),([\d\.]+)""".r

  def runHey: Future[List[Double]] = Future {
    val url = s"http://${ conf.endpoint.address }:${ conf.endpoint.port }/stress"
    val cmd = "hey -n 500 -c 10 -t 10"
    val csvCmd = s"$cmd -o csv $url"
    // First run, for UI
    val runCmd = s"$cmd $url"
    log.info(s"Querying $url")
    Process(runCmd).!
    // Second run, for stats
    val stream = csvCmd lineStream_! ProcessLogger(_ => ())
    val values = stream.flatMap { (line) => line match {
        case CsvPattern(responseTime, dnsLookup, dns, requestWrite, responseDelay, responseRead) =>
          Some(responseTime.toDouble * 1000)
        case _ => None
      }
    }
    values.toList
  }

  override def postStop(): Unit = {
    httpBinding match {
      case Some(x) => x.unbind
      case None =>
    }
  }

  override def receive: Receive = {
    case HttpStart =>
      httpClient = Some(new HttpClient(
        conf.dashboard.address,
        conf.dashboard.port,
        Some("dashboard/index.html"),
        self
      ))

    case HttpRoute =>
      sender() ! path("mqtt") {
        get {
          val entries = cassandraClient.recentAll()
          val json = serializer.toJson(entries)
          complete(HttpEntity(ContentTypes.`application/json`, json))
        }
      } ~ path("history") {
        get {
          val entries = cassandraClient.historyAll()
          val json = serializer.toJson(entries)
          complete(HttpEntity(ContentTypes.`application/json`, json))
        }
      } ~ path("perf") {
        get {
          onSuccess(getPerf) { perf =>
            val json = serializer.toJson(perf)
            complete(HttpEntity(ContentTypes.`application/json`, json))
          }
        }
      }

    case HttpConnected(binding) =>
      httpBinding = Some(binding)

    case HttpConnectionFailure(ex) =>
      log.error(s"Failed to establish HTTP connection $ex")
  }
}

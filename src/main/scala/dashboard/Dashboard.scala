package dashboard

import akka.actor._
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import analyzer.Endpoint.Stats
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import lib.CassandraClient.{HistoryAll, RecentAll}
import lib._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.sys.process._

object Dashboard {
  def props(cassandraClient: ActorRef, endpoint: ActorRef)
           (implicit materializer: ActorMaterializer) =
    Props(classOf[Dashboard], cassandraClient, endpoint, materializer)

  final case class Perf(timings: List[Double], actorStats: Map[String, Double])
}

class Dashboard(cassandraClient: ActorRef, endpoint: ActorRef)
               (implicit materializer: ActorMaterializer)
  extends Actor with ActorLogging {
  import Dashboard._

  implicit val system: ActorSystem = context.system
  implicit val executionContext: ExecutionContext = system.dispatcher

  private val conf = Config.get
  implicit val timeout: Timeout = Timeout(conf.dashboard.timeout.millis)

  val mapper = new ObjectMapper with ScalaObjectMapper
  mapper.registerModule(DefaultScalaModule)

  private val route =
    path("mqtt") {
      get {
        onSuccess(cassandraClient ? RecentAll) { entries =>
          val json = mapper.writeValueAsString(entries)
          complete(HttpEntity(ContentTypes.`application/json`, json))
        }
      }
    } ~ path("history") {
      get {
        onSuccess(cassandraClient ? HistoryAll) { entries =>
          val json = mapper.writeValueAsString(entries)
          complete(HttpEntity(ContentTypes.`application/json`, json))
        }
      }
    } ~ path("perf") {
      get {
        onSuccess(getPerf) { perf =>
          val json = mapper.writeValueAsString(perf)
          complete(HttpEntity(ContentTypes.`application/json`, json))
        }
      }
    }

  var httpBinding: Option[ServerBinding] = None
  val client = new HttpClient(
    route,
    conf.dashboard.address,
    conf.dashboard.port,
    Some("dashboard/index.html"),
    self
  )

  def getPerf: Future[Perf] =
    runHey flatMap (timings =>
      ask(endpoint, Stats).mapTo[Map[String, Double]] map (Perf(timings, _))
    )

  private val CsvPattern = raw"""([\d\.]+),([\d\.]+),([\d\.]+),([\d\.]+),([\d\.]+),([\d\.]+)""".r

  def runHey: Future[List[Double]] = Future {
    val url = s"http://${ conf.endpoint.address }:${ conf.endpoint.port }/"
    val cmd = "hey -n 500 -c 10 -t 10"
    val csvCmd = s"$cmd -o csv $url"
    // First run, for UI
    val runCmd = s"$cmd $url"
    log.info(s"Querying $url")
    Process(runCmd).!
    // Second run, for stats
    val stream = csvCmd lineStream_! ProcessLogger(line => ())
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
    case Connected(binding) =>
      httpBinding = Some(binding)
    case ConnectionFailure(ex) =>
      log.error(s"Failed to establish HTTP connection $ex")
  }
}

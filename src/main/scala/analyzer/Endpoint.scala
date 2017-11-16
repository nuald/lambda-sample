package analyzer

import akka.actor._
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import lib._

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

object Endpoint {
  def props(analyzerOpt: Option[ActorRef])
           (implicit materializer: ActorMaterializer) =
    Props(classOf[Endpoint], analyzerOpt, materializer)

  final case object Stats
}

class Endpoint(analyzerOpt: Option[ActorRef])
              (implicit materializer: ActorMaterializer)
  extends Actor with ActorLogging {
  import Endpoint._

  implicit val system: ActorSystem = context.system
  implicit val executionContext: ExecutionContext = system.dispatcher

  private val conf = Config.get
  implicit val timeout: Timeout = Timeout(conf.endpoint.timeout.millis)

  val mapper = new ObjectMapper with ScalaObjectMapper
  mapper.registerModule(DefaultScalaModule)

  private var analyzers = analyzerOpt match {
    case Some(ref) => IndexedSeq(ref)
    case None => IndexedSeq()
  }
  private val stats = mutable.Map[String, Int]()
  var jobCounter = 0

  def getAnalyzer: ActorRef = {
    jobCounter += 1
    val analyzer = analyzers(jobCounter % analyzers.size)
    val name = analyzer.toString
    val count = stats.getOrElse(name, 0)
    stats(name) = count + 1
    analyzer
  }

  def getStats: Map[String, Double] = {
    val sum = stats.values.sum
    val mapped = stats map {case (k, v) => (k, v.toDouble / sum)}
    mapped.toMap
  }

  private val route =
    pathSingleSlash {
      get {
        if (analyzers.nonEmpty) {
          onSuccess(getAnalyzer ? Analyze) { entries  =>
            val json = mapper.writeValueAsString(entries)
            complete(HttpEntity(ContentTypes.`application/json`, json))
          }
        } else {
          complete(StatusCodes.InternalServerError, "No analyzer has been registered!")
        }
      }
    }

  var httpBinding: Option[ServerBinding] = None
  val httpClient = new HttpClient(
    route,
    conf.endpoint.address,
    conf.endpoint.port,
    None,
    self
  )

  override def postStop(): Unit = {
    httpBinding match {
      case Some(x) => x.unbind
      case None =>
    }
  }

  override def receive: Receive = {
    case Stats =>
      sender() ! getStats

    case Connected(binding) =>
      httpBinding = Some(binding)

    case ConnectionFailure(ex) =>
      log.error(s"Failed to establish HTTP connection $ex")

    case Registration if !analyzers.contains(sender()) =>
      context watch sender()
      analyzers = analyzers :+ sender()

    case Terminated(a) =>
      analyzers = analyzers.filterNot(_ == a)
  }
}

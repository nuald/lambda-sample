package analyzer

import akka.actor._
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import lib._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

object Endpoint {
  def props(fastAnalyzer: ActorRef)(implicit materializer: ActorMaterializer) =
    Props(classOf[Endpoint], fastAnalyzer, materializer)

  final case object Analyze
}

class Endpoint(fastAnalyzer: ActorRef)(implicit materializer: ActorMaterializer)
  extends Actor with ActorLogging {
  import Endpoint._

  implicit val system: ActorSystem = context.system
  implicit val executionContext: ExecutionContext = system.dispatcher

  private val conf = Config.get
  implicit val timeout: Timeout = Timeout(conf.endpoint.timeout.millis)

  val mapper = new ObjectMapper with ScalaObjectMapper
  mapper.registerModule(DefaultScalaModule)

  private val route =
    pathSingleSlash {
      get {
        onSuccess(ask(fastAnalyzer, Analyze).mapTo[Seq[SensorMeta]]) { entries  =>
          val json = mapper.writeValueAsString(entries)
          complete(HttpEntity(ContentTypes.`application/json`, json))
        }
      }
    }

  var httpBinding: Option[ServerBinding] = None
  val client = new HttpClient(
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
    case Connected(binding) =>
      httpBinding = Some(binding)
    case ConnectionFailure(ex) =>
      log.error(s"Failed to establish HTTP connection $ex")
  }
}

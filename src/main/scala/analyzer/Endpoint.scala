package analyzer

import akka.actor._
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import analyzer.Analyzer.Registration
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import lib._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

case object Analyze

object Endpoint {
  def props(analyzer: ActorRef)(implicit materializer: ActorMaterializer) =
    Props(classOf[Endpoint], analyzer, materializer)

}

class Endpoint(analyzer: ActorRef)(implicit materializer: ActorMaterializer)
  extends Actor with ActorLogging {
  import Endpoint._

  implicit val system: ActorSystem = context.system
  implicit val executionContext: ExecutionContext = system.dispatcher

  private val conf = Config.get
  implicit val timeout: Timeout = Timeout(conf.endpoint.timeout.millis)

  val mapper = new ObjectMapper with ScalaObjectMapper
  mapper.registerModule(DefaultScalaModule)

  private var analyzers = IndexedSeq(analyzer)
  var jobCounter = 0

  private val route =
    pathSingleSlash {
      get {
        if (analyzers.nonEmpty) {
          jobCounter += 1
          val analyzer = analyzers(jobCounter % analyzers.size)
          onSuccess(ask(analyzer, Analyze).mapTo[Seq[SensorMeta]]) { entries  =>
            val json = mapper.writeValueAsString(entries)
            complete(HttpEntity(ContentTypes.`application/json`, json))
          }
        } else {
          complete(StatusCodes.InternalServerError, "No analyzer has been registered!")
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

    case Registration if !analyzers.contains(sender()) =>
      context watch sender()
      analyzers = analyzers :+ sender()

    case Terminated(a) =>
      analyzers = analyzers.filterNot(_ == a)
  }
}

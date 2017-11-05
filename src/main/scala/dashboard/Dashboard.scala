package dashboard

import akka.actor._
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import lib.CassandraClient.{HistoryAll, RecentAll}
import lib._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

object Dashboard {
  def props(cassandraClient: ActorRef)(implicit materializer: ActorMaterializer) =
    Props(classOf[Dashboard], cassandraClient, materializer)
}

class Dashboard(cassandraClient: ActorRef)(implicit materializer: ActorMaterializer)
  extends Actor with ActorLogging {

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
    }

  var httpBinding: Option[ServerBinding] = None
  val client = new HttpClient(
    route,
    conf.dashboard.address,
    conf.dashboard.port,
    Some("dashboard/index.html"),
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

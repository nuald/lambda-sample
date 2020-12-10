package lib

import akka.actor._
import akka.pattern.ask
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives._
import akka.http.scaladsl.server.Directives.{path, _}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import java.nio.file.Paths

import ContentTypeResolver.Default
import akka.util.Timeout

case object HttpStart
case object HttpRoute
final case class HttpConnected(binding: ServerBinding)
final case class HttpConnectionFailure(ex: Throwable)

class HttpClient(
  address: String,
  port: Int,
  index: Option[String],
  supervisor: ActorRef
)(implicit
  system: ActorSystem,
  executionContext: ExecutionContext
) {
  implicit val timeout: Timeout = Timeout(1.seconds)

  supervisor.ask(HttpRoute).mapTo[Route] foreach { handler =>
    def cdnExtended = {
      val cdnHandler = handler ~
        path("cdn" / Segments) { segments =>
          getFromFile(Paths.get("resources", segments: _*).toString)
        }

      index match {
        case Some(path) => cdnHandler ~
          pathSingleSlash {
            getFromFile(Paths.get("resources", path).toString)
          }
        case None => cdnHandler
      }
    }

    Http().newServerAt(address, port).bind(cdnExtended).onComplete {
      case Success(binding) => supervisor ! HttpConnected(binding)
      case Failure(ex) => supervisor ! HttpConnectionFailure(ex)
    }
  }
}

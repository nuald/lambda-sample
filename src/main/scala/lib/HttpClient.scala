package lib

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow

import scala.concurrent.ExecutionContext
import scala.util._

import java.nio.file.Paths

import ContentTypeResolver.Default

final case class Connected(binding: ServerBinding)
final case class ConnectionFailure(ex: Throwable)

object HttpClient {
  def apply(
    handler: Route,
    address: String,
    port: Int,
    index: String,
    supervisor: ActorRef
  )(implicit
    system: ActorSystem,
    materializer: ActorMaterializer,
    executionContext: ExecutionContext
  ) = {
    new HttpClient(handler, address, port, index, supervisor)
      (system, materializer, executionContext)
  }
}

class HttpClient(
  handler: Route,
  address: String,
  port: Int,
  index: String,
  supervisor: ActorRef
)(implicit
  system: ActorSystem,
  materializer: ActorMaterializer,
  executionContext: ExecutionContext
) {
  var cdnExtended = handler ~
    path("cdn" / Segment) { path =>
      getFromFile(Paths.get("resources", path).toString)
    } ~
    pathSingleSlash {
      getFromFile(Paths.get("resources", index).toString)
    }

  Http().bindAndHandle(cdnExtended, address, port).onComplete {
    case Success(binding) => supervisor ! Connected(binding)
    case Failure(ex) => supervisor ! ConnectionFailure(ex)
  }
}

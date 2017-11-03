package lib

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow

import scala.concurrent.ExecutionContext
import scala.util._

final case class Connected(binding: ServerBinding)
final case class ConnectionFailure(ex: Throwable)

object HttpClient {
  def apply(
    handler: Flow[HttpRequest, HttpResponse, _],
    address: String,
    port: Int,
    supervisor: ActorRef
  )(implicit
    system: ActorSystem,
    materializer: ActorMaterializer,
    executionContext: ExecutionContext
  ) = {
    new HttpClient(handler, address, port, supervisor)
      (system, materializer, executionContext)
  }
}

class HttpClient(
  handler: Flow[HttpRequest, HttpResponse, _],
  address: String,
  port: Int,
  supervisor: ActorRef
)(implicit
  system: ActorSystem,
  materializer: ActorMaterializer,
  executionContext: ExecutionContext
) {
  Http().bindAndHandle(handler, address, port).onComplete {
    case Success(binding) => supervisor ! Connected(binding)
    case Failure(ex) => supervisor ! ConnectionFailure(ex)
  }
}

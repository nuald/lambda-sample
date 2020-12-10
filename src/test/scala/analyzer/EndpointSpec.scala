package analyzer

import akka.actor.{Actor, Props}
import akka.pattern.ask
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.util.Timeout

import org.scalatest._
import matchers.should._

import lib.HttpRoute

import scala.concurrent.duration._

class EndpointSpec extends wordspec.AnyWordSpec
    with Matchers with ScalatestRouteTest {
  implicit val timeout: Timeout = Timeout(5.seconds)

  "The endpoint" should {

    "return a HTTP error if no analyzer has been registered" in {
      val endpoint = system.actorOf(Endpoint.props(None))
      endpoint.ask(HttpRoute).mapTo[Route] foreach { route =>
        Get() ~> route ~> check {
          status shouldEqual StatusCodes.InternalServerError
        }
      }
    }

    "return a JSON string based on the analyzers results" in {
      val analyzer = system.actorOf(Props(new Actor {
        override def receive: Receive = {
          case Analyze => AllMeta(List())
        }
      }))
      val endpoint = system.actorOf(Endpoint.props(None))
      analyzer.ask(endpoint, Registration) foreach { _ =>
        endpoint.ask(HttpRoute).mapTo[Route] foreach { route =>
          Get() ~> route ~> check {
            responseAs[String] shouldEqual "{\"entries\":[]}"
          }
        }
      }
    }
  }
}

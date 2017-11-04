package analyzer

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.pattern.{ask, pipe}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import analyzer.Endpoint.Analyze
import lib.CassandraClient.{Entry, Recent}
import lib.Config

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.collection.JavaConverters._

object FastAnalyzer {
  def props(cassandraClient: ActorRef)(implicit materializer: ActorMaterializer) =
    Props(classOf[FastAnalyzer], cassandraClient, materializer)
}

class FastAnalyzer(cassandraClient: ActorRef)(implicit materializer: ActorMaterializer)
  extends Actor with ActorLogging {
  import FastAnalyzer._

  implicit val system: ActorSystem = context.system
  implicit val executionContext: ExecutionContext = system.dispatcher

  private val conf = Config.get
  implicit val timeout: Timeout = Timeout(conf.fastAnalyzer.timeout.millis)

  def hasAnomaly(entries: List[Entry]): Boolean = true

  override def receive: Receive = {
    case Analyze =>
      val futures: Seq[Future[Option[String]]] =
        for (sensor <- conf.mqtt.sensors.asScala)
          yield for {
            entries <- ask(cassandraClient, Recent(sensor)).mapTo[List[Entry]]
          } yield if (hasAnomaly(entries)) Some(sensor) else None

      val result: Future[Seq[Option[String]]] = Future.sequence(futures)
      result pipeTo sender()
  }
}

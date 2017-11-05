import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import analyzer.{Endpoint, FastAnalyzer, HistoryWriter}
import com.datastax.driver.core.Cluster

import scala.concurrent.Await
import scala.concurrent.duration._
import dashboard._
import mqtt._
import lib._

object Main extends App {
  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val conf = Config.get
  val cluster = Cluster.builder().addContactPoint(conf.cassandra.address).build()

  system.actorOf(Producer.props(), "producer")
  system.actorOf(Consumer.props(cluster), "consumer")

  val cassandraClient = system.actorOf(CassandraClient.props(cluster), "cassandra-client")
  val fastAnalyzer = system.actorOf(FastAnalyzer.props(cassandraClient), "fast-analyzer")
  system.actorOf(Endpoint.props(fastAnalyzer), "endpoint")
  system.actorOf(HistoryWriter.props(cluster, fastAnalyzer), "history-writer")

  system.actorOf(Dashboard.props(cassandraClient), "dashboard")

  scala.sys.addShutdownHook {
    system.terminate()
    Await.result(system.whenTerminated, 5.seconds)
    cluster.close()
  }
}

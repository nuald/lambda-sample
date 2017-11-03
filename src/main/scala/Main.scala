import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import com.datastax.driver.core.Cluster

import scala.concurrent.Await
import scala.concurrent.duration._

import dashboard._
import mqtt._
import lib._

object Main extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  val conf = Config.get
  val addr = conf.cassandra.address
  val cluster = Cluster.builder().addContactPoint(addr).build()

  system.actorOf(Producer.props(), "producer")
  system.actorOf(Consumer.props(cluster), "consumer")
  system.actorOf(Dashboard.props(cluster), "dashboard")

  scala.sys.addShutdownHook {
    system.terminate()
    Await.result(system.whenTerminated, 5.seconds)
    cluster.close
  }
}

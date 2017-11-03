package dashboard

import akka.actor._
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives._
import akka.stream.ActorMaterializer

import ammonite.ops._

import com.datastax.driver.core.Cluster
import com.datastax.driver.core.querybuilder.QueryBuilder
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule

import scala.collection.JavaConverters._
import scala.io.StdIn
import scala.util._

import ContentTypeResolver.Default
import lib._

case class Entry(sensor: String, ts: java.util.Date, value: Int)

object Dashboard {
  def props(cluster: Cluster)(implicit materializer: ActorMaterializer) =
    Props(classOf[Dashboard], cluster, materializer)
}

class Dashboard(cluster: Cluster)(implicit materializer: ActorMaterializer)
  extends Actor with ActorLogging {

  implicit private val system = context.system
  implicit private val executionContext = system.dispatcher

  // get rid of conf and logger
  val conf = Config.get
  val keyspace = conf.cassandra.keyspace
  val table = conf.cassandra.table
  val sensors = conf.mqtt.sensors

  val mapper = new ObjectMapper with ScalaObjectMapper
  mapper.registerModule(DefaultScalaModule)

  val session = cluster.connect(keyspace)

  val route =
    path("mqtt") {
      get {
        val entries = for (sensor <- sensors.asScala)
          yield {
            val query = QueryBuilder.select().all().from(table)
              .where(QueryBuilder.eq("sensor", sensor))
              .limit(conf.dashboard.limit)
            val rs = session.execute(query)

            for (row <- rs.asScala)
              yield Entry(
                row.getString("sensor"),
                row.getTimestamp("ts"),
                row.getInt("value")
              )
          }

        val json = mapper.writeValueAsString(entries.flatten)
        complete(HttpEntity(ContentTypes.`application/json`, json))
      }
    } ~
    path("cdn" / Segment) { path =>
      val file = pwd / 'resources / 'dashboard / path
      getFromFile(file.toString)
    } ~
    path("") {
      val file = pwd / 'resources / 'dashboard / "index.html"
      getFromFile(file.toString)
    }

  var httpBinding: Option[ServerBinding] = None
  val client = HttpClient(
    route, conf.dashboard.address, conf.dashboard.port, self)

  override def postStop() = {
    httpBinding match {
      case Some(x) => x.unbind
      case None =>
    }
    session.close
  }

  override def receive: Receive = {
    case Connected(binding) =>
      httpBinding = Some(binding)
    case ConnectionFailure(ex) =>
      // TODO: correctly handle the error
      log.error(s"Failed to establish HTTP connection $ex")
  }
}

package dashboard

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
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

object WebServer extends App {
  val conf = Config.get
  implicit val logger = getLogger(conf.logger)
  implicit val system = ActorSystem("dashboard")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val addr = conf.cassandra.address
  val keyspace = conf.cassandra.keyspace
  val table = conf.cassandra.table
  val sensors = conf.mqtt.sensors

  val mapper = new ObjectMapper with ScalaObjectMapper
  mapper.registerModule(DefaultScalaModule)

  val cluster = Cluster.builder().addContactPoint(addr).build()

  val route =
    path("mqtt") {
      get {
        using(cluster.connect(keyspace))(_.close) { session =>
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

          mapper.writeValueAsString(entries.flatten)
        } match {
          case Success(v) =>
            complete(HttpEntity(ContentTypes.`application/json`, v))
          case Failure(e) =>
            logger.error(s"Reading Cassandra failed", e)
            complete(HttpResponse(StatusCodes.InternalServerError, entity = e.toString))
        }
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

  val bindingFuture = Http().bindAndHandle(
    route, conf.dashboard.address, conf.dashboard.port
  )
  logger.info("Press <Enter> to exit")
  StdIn.readLine()

  bindingFuture
    .flatMap(_.unbind())
    .onComplete {_ =>
      cluster.close
      system.terminate
    }
}

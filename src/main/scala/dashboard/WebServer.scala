package dashboard

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import ammonite.ops._
import com.datastax.driver.core.Cluster
import com.datastax.driver.core.querybuilder.QueryBuilder
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import lib._
import scala.collection.JavaConverters._
import scala.io.StdIn
import scala.util._

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

  val mapper = new ObjectMapper with ScalaObjectMapper
  mapper.registerModule(DefaultScalaModule)

  val cluster = Cluster.builder().addContactPoint(addr).build()

  val route =
    path("mqtt") {
      get {
        using(cluster.connect(keyspace))(_.close) { session =>
          val query = QueryBuilder.select().all().from(table).limit(100)
          val rs = session.execute(query)

          val entries = for (row <- rs.asScala)
            yield Entry(
              row.getString("sensor"),
              row.getTimestamp("ts"),
              row.getInt("value")
            )

          mapper.writeValueAsString(entries)
        } match {
          case Success(v) =>
            complete(HttpEntity(ContentTypes.`application/json`, v))
          case Failure(e) =>
            logger.error(s"Reading Cassandra failed", e)
            complete(HttpResponse(StatusCodes.InternalServerError, entity = e.toString))
        }
      }
    } ~
    path("") {
      get {
        val file = pwd / 'resources / 'dashboard/ "index.html"
        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, read! file))
      }
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

package lib

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper

class JsonSerializer {
  private[this] val mapper = new ObjectMapper with ScalaObjectMapper
  mapper.registerModule(DefaultScalaModule)

  def toJson(obj: AnyRef): String = {
    mapper.writeValueAsString(obj)
  }
}

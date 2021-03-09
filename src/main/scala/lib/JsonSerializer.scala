package lib

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule

class JsonSerializer {
  private[this] val mapper = JsonMapper.builder()
    .addModule(DefaultScalaModule)
    .addModule(new JavaTimeModule())
    .build()

  def toJson(obj: AnyRef): String = {
    mapper.writeValueAsString(obj)
  }
}

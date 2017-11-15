package lib

import akka.serialization.Serializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.smile.SmileFactory
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper

class ClusterSerializer extends Serializer {
  val mapper = new ObjectMapper(new SmileFactory()) with ScalaObjectMapper
  mapper.registerModule(DefaultScalaModule)

  override def identifier = 1023

  override def toBinary(obj: AnyRef): Array[Byte] = {
    mapper.writeValueAsBytes(obj)
  }

  override def includeManifest = true

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    manifest match {
      case Some(cls) => mapper.readValue(bytes, cls.getClass)
      case None => null
    }
  }
}

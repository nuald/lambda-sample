package lib

import akka.serialization.SerializerWithStringManifest
import analyzer.Analyze
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.smile.SmileFactory
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper

class ClusterSerializer extends SerializerWithStringManifest {
  val mapper = new ObjectMapper(new SmileFactory()) with ScalaObjectMapper
  mapper.registerModule(DefaultScalaModule)

  val AnalyzeManifest = "analyze"

  override def identifier = 1023

  override def toBinary(obj: AnyRef): Array[Byte] = {
    mapper.writeValueAsBytes(obj)
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = {
    manifest match {
      case AnalyzeManifest => Analyze
    }
  }

  override def manifest(obj: AnyRef): String = {
    obj match {
      case _: Analyze.type => AnalyzeManifest
    }
  }
}

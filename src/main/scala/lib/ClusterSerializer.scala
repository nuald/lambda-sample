package lib

import akka.serialization.SerializerWithStringManifest
import analyzer.{AllMeta, Analyze, Registration, SensorMeta}
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.smile.SmileFactory
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import mqtt.Producer.MqttEntry
import smile.classification.RandomForest

object ClusterSerializer {
  val AnalyzeManifest: String = Analyze.getClass.getName
  val RegistrationManifest: String = Registration.getClass.getName
  val AllMetaManifest: String = AllMeta.getClass.getName
  val RandomForestManifest: String = classOf[RandomForest].getName
  val SensorMetaManifest: String = SensorMeta.getClass.getName
  val MqttEntryManifest: String = MqttEntry.getClass.getName
}

class ClusterSerializer extends SerializerWithStringManifest {
  import ClusterSerializer._

  val mapper = new ObjectMapper(new SmileFactory()) with ScalaObjectMapper
  mapper.registerModule(DefaultScalaModule)

  override def identifier = 1023

  override def toBinary(obj: AnyRef): Array[Byte] = {
    mapper.writeValueAsBytes(obj)
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = {
    manifest match {
      case AnalyzeManifest => Analyze
      case RegistrationManifest => Registration
      case AllMetaManifest => mapper.readValue[AllMeta](bytes)
      case RandomForestManifest => mapper.readValue[RandomForest](bytes)
      case SensorMetaManifest => mapper.readValue[SensorMeta](bytes)
      case MqttEntryManifest => mapper.readValue[MqttEntry](bytes)
    }
  }

  override def manifest(obj: AnyRef): String = {
    obj match {
      case _: Analyze.type => AnalyzeManifest
      case _: Registration.type => RegistrationManifest
      case _: AllMeta => AllMetaManifest
      case _: RandomForest => RandomForestManifest
      case _: SensorMeta => SensorMetaManifest
      case _: MqttEntry => MqttEntryManifest
    }
  }
}

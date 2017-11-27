package lib

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}

import akka.event.LoggingAdapter
import akka.serialization.SerializerWithStringManifest
import analyzer._
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.smile.SmileFactory
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import mqtt.Producer.MqttEntry
import smile.classification.RandomForest

import scala.util.Try

object BinarySerializer {
  private val AnalyzeManifest: String = Analyze.getClass.getName
  private val StressAnalyzeManifest: String = StressAnalyze.getClass.getName
  private val RegistrationManifest: String = Registration.getClass.getName
  private val AllMetaManifest: String = AllMeta.getClass.getName

  val RandomForestManifest: String = classOf[RandomForest].getName
  val SensorMetaManifest: String = SensorMeta.getClass.getName
  val MqttEntryManifest: String = MqttEntry.getClass.getName

  // Jackson can't serialize Java Serializable in cases
  // like an absent default constructor, therefore need to track it
  private val JavaManifests = Set(RandomForestManifest)
}

class BinarySerializer extends SerializerWithStringManifest {
  import BinarySerializer._
  import lib.Common.using

  private[this] val mapper = new ObjectMapper(new SmileFactory()) with ScalaObjectMapper
  mapper.registerModule(DefaultScalaModule)

  implicit val logger: LoggingAdapter = akka.event.NoLogging

  override def identifier = 1023

  override def toBinary(obj: AnyRef): Array[Byte] = {
    if (JavaManifests.contains(manifest(obj))) {
      javaSerialize(obj).get
    } else {
      mapper.writeValueAsBytes(obj)
    }
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = {
    if (JavaManifests.contains(manifest)) {
      javaDeserialize(bytes).get
    } else {
      manifest match {
        case AnalyzeManifest => Analyze
        case StressAnalyzeManifest => StressAnalyze
        case RegistrationManifest => Registration
        case AllMetaManifest => mapper.readValue[AllMeta](bytes)
        case SensorMetaManifest => mapper.readValue[SensorMeta](bytes)
        case MqttEntryManifest => mapper.readValue[MqttEntry](bytes)
      }
    }
  }

  override def manifest(obj: AnyRef): String = {
    obj match {
      case _: Analyze.type => AnalyzeManifest
      case _: StressAnalyze.type => StressAnalyzeManifest
      case _: Registration.type => RegistrationManifest
      case _: AllMeta => AllMetaManifest
      case _: RandomForest => RandomForestManifest
      case _: SensorMeta => SensorMetaManifest
      case _: MqttEntry => MqttEntryManifest
    }
  }

  private[this] def javaSerialize(obj: AnyRef): Try[Array[Byte]] =
    using(new ByteArrayOutputStream())(_.close) { ostream =>
      using(new ObjectOutputStream(ostream))(_.close) { outputStream =>
        outputStream.writeObject(obj)
      }
      ostream.toByteArray
    }

  private[this] def javaDeserialize(bytes: Array[Byte]): Try[AnyRef] = {
    using(new ObjectInputStream(
      new ByteArrayInputStream(bytes)))(_.close) { inputStream =>
      inputStream.readObject()
    }
  }
}

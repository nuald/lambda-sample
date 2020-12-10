package lib

import analyzer._
import lib.BinarySerializer._
import lib.EntriesFixture.Precision
import mqtt.Producer.MqttEntry

import org.scalactic.Equality

import org.scalatest._
import matchers.should._

import smile.classification.{RandomForest, randomForest}

class SerializerSpec extends flatspec.AnyFlatSpec with Matchers {

  val serializer = new BinarySerializer()

  private[this] def fixture = EntriesFixture()

  "The binary serializer" should "process Analyze object correctly" in {
    val obj = Analyze
    val bytes = serializer.toBinary(obj)
    val ref = serializer.fromBinary(bytes, AnalyzeManifest)
    obj should be (ref)
  }

  it should "process StressAnalyze object correctly" in {
    val obj = StressAnalyze
    val bytes = serializer.toBinary(obj)
    val ref = serializer.fromBinary(bytes, StressAnalyzeManifest)
    obj should be (ref)
  }

  it should "process Registration object correctly" in {
    val obj = Registration
    val bytes = serializer.toBinary(obj)
    val ref = serializer.fromBinary(bytes, RegistrationManifest)
    obj should be (ref)
  }

  it should "process AllMeta object correctly" in {
    val obj = AllMeta(List())
    val bytes = serializer.toBinary(obj)
    val ref = serializer.fromBinary(bytes, AllMetaManifest)
    obj should be (ref)
  }

  it should "process RandomForest object correctly" in {
    val f = fixture
    val obj = randomForest(f.formula, f.data, ntrees = 1)
    val bytes = serializer.toBinary(obj)
    val ref = serializer.fromBinary(bytes, RandomForestManifest)
      .asInstanceOf[smile.classification.RandomForest]
    obj should equal (ref)
  }

  it should "process SensorMeta object correctly" in {
    val obj = SensorMeta("", java.time.Instant.now(), 0, 0, 0)
    val bytes = serializer.toBinary(obj)
    val ref = serializer.fromBinary(bytes, SensorMetaManifest)
    obj should be (ref)
  }

  it should "process MqttEntry object correctly" in {
    val obj = MqttEntry("", 0, 0)
    val bytes = serializer.toBinary(obj)
    val ref = serializer.fromBinary(bytes, MqttEntryManifest)
    obj should be (ref)
  }
}

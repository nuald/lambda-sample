package lib

import java.util.Date

import analyzer.ML.{DecisionTree, DecisionTreeNode, RandomForest, RandomForestTree}
import analyzer._
import lib.BinarySerializer._
import mqtt.Producer.MqttEntry
import org.scalactic.Equality
import org.scalatest.{FlatSpec, Matchers}
import smile.classification.randomForest
import lib.EntriesFixture.Precision

class SerializerSpec extends FlatSpec with Matchers {

  implicit val randomForestEq: Equality[RandomForest] =
    (a: RandomForest, b: Any) => b match {
      case p: RandomForest =>
        a.trees.zip(p.trees).forall(x => x._1 === x._2) && a.k == p.k
      case _ => false
    }

  implicit val randomForestTreeEq: Equality[RandomForestTree] =
    (a: RandomForestTree, b: Any) => b match {
      case p: RandomForestTree =>
        a.tree === p.tree && a.weight === p.weight +- Precision
      case _ => false
    }

  implicit val decisionTreeEq: Equality[DecisionTree] =
    (a: DecisionTree, b: Any) => b match {
      case p: DecisionTree =>
        a.attributes.zip(p.attributes).forall(x => x._1 === x._2) &&
          a.root === p.root
      case _ => false
    }

  implicit val decisionTreeNodeEq: Equality[DecisionTreeNode] =
    (a: DecisionTreeNode, b: Any) => b match {
      case p: DecisionTreeNode =>
        val posterioriEq = a.posteriori.zip(p.posteriori).forall(x => x._1 === x._2 +- Precision)
        val splitFeatureEq = a.splitFeature == p.splitFeature
        val splitValueEq = (a.splitValue.isNaN && p.splitValue.isNaN) ||
            a.splitValue === p.splitValue +- Precision
        val trueChildEq = (a.trueChild.isEmpty && p.trueChild.isEmpty) ||
          a.trueChild.get === p.trueChild.get
        val falseChildEq = (a.falseChild.isEmpty && p.falseChild.isEmpty) ||
          a.falseChild.get === p.falseChild.get
        posterioriEq && splitFeatureEq && splitValueEq && trueChildEq && falseChildEq
      case _ => false
    }

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
    val obj = randomForest(f.features.toArray, f.labels.toArray, ntrees = 1)
    val mlObj = RandomForest(obj)
    val bytes = serializer.toBinary(obj)
    val ref = serializer.fromBinary(bytes, RandomForestManifest)
      .asInstanceOf[smile.classification.RandomForest]
    val mlRef = RandomForest(ref)
    mlObj should equal (mlRef)
  }

  it should "process SensorMeta object correctly" in {
    val obj = SensorMeta("", new Date(), 0, 0, 0)
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

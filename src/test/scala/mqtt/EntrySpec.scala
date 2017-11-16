package mqtt

import akka.event.LoggingAdapter
import lib.BinarySerializer
import mqtt.Producer.MqttEntry
import org.scalatest._

class EntrySpec extends FlatSpec with Matchers {
  implicit val logger: LoggingAdapter = akka.event.NoLogging

  "The Entry object" should "be sealed into the same byte array given the same args" in {
    val sensor = "sensor 1"
    val value = 123
    val anomaly = 0
    val serializer = new BinarySerializer()
    val bytes1 = serializer.toBinary(MqttEntry(sensor, value, anomaly))
    val bytes2 = serializer.toBinary(MqttEntry(sensor, value, anomaly))
    bytes1 should contain theSameElementsInOrderAs bytes2
  }

  it should "be unsealed with the same values" in {
    val sensor = "sensor 1"
    val value = 123
    val anomaly = 0
    val serializer = new BinarySerializer()

    val originalEntry = MqttEntry(sensor, value, anomaly)
    val bytes = serializer.toBinary(originalEntry)
    val entry = serializer.fromBinary(bytes, BinarySerializer.MqttEntryManifest)
    entry should be (originalEntry)
  }
}

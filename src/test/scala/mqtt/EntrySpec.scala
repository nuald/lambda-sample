package mqtt

import akka.event.LoggingAdapter
import lib.Sealed
import org.scalatest._

class EntrySpec extends FlatSpec with Matchers {
  implicit val logger: LoggingAdapter = akka.event.NoLogging

  "The Entry object" should "be sealed into the same byte array given the same args" in {
    val sensor = "sensor 1"
    val value = 123
    val anomaly = 0
    val sealWriter = new Sealed[Entry]("salt").writer
    val bytes1 = sealWriter(mqtt.Entry(sensor, value, anomaly)).get
    val bytes2 = sealWriter(mqtt.Entry(sensor, value, anomaly)).get
    bytes1 should contain theSameElementsInOrderAs bytes2
  }

  it should "be unsealed with the same values" in {
    val sensor = "sensor 1"
    val value = 123
    val anomaly = 0
    val seal = new Sealed[Entry]("salt")
    val sealWriter = seal.writer
    val sealReader = seal.reader

    val originalEntry = mqtt.Entry(sensor, value, anomaly)
    val bytes = sealWriter(originalEntry).get
    val entry = sealReader(bytes).get
    entry should be (originalEntry)
  }
}

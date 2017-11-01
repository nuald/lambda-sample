package mqtt

import org.scalatest._

class EntrySpec extends FlatSpec with Matchers {
  "The Entry object" should "serialize into the same byte array given the same args" in {
    val sensor = "sensor 1"
    val value = 123
    val signature = Array[Byte]()
    val bytes1 = Entry(sensor, value, signature).toBytes
    val bytes2 = Entry(sensor, value, signature).toBytes
    bytes1 should contain theSameElementsInOrderAs bytes2
  }

  it should "have the same signature given the same args" in {
    val sensor = "sensor 1"
    val value = 123
    val factory = new EntryFactory("salt")
    val signature1 = factory.create(sensor, value).signature
    val signature2 = factory.create(sensor, value).signature
    signature1 should contain theSameElementsInOrderAs signature2
  }

  it should "have the same signature from different factories" in {
    val sensor = "sensor 1"
    val value = 123
    val salt = "salt"
    val signature1 = new EntryFactory(salt).create(sensor, value).signature
    val signature2 = new EntryFactory(salt).create(sensor, value).signature
    signature1 should contain theSameElementsInOrderAs signature2
  }

  it should "have the signatures comparable with sameElements()" in {
    val sensor = "sensor 1"
    val value = 123
    val factory = new EntryFactory("salt")
    val signature1 = factory.create(sensor, value).signature
    val signature2 = factory.create(sensor, value).signature
    signature1.sameElements(signature2) should be (true)
  }
}

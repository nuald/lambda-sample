package mqtt

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.dataformat.smile.SmileFactory

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

case class Entry(sensor: String, value: Double, anomaly: Int, signature: Array[Byte]) {
  def toBytes: Array[Byte] = {
    val mapper = new ObjectMapper(new SmileFactory) with ScalaObjectMapper
    mapper.registerModule(DefaultScalaModule)
    mapper.writeValueAsBytes(this)
  }
}

class EntryFactory(salt: String) {
  val HmacAlgorithm = "HmacSHA256"
  val secret = new SecretKeySpec(salt.getBytes, HmacAlgorithm)

  private val mac = Mac.getInstance(HmacAlgorithm)
  mac.init(secret)

  val mapper = new ObjectMapper(new SmileFactory) with ScalaObjectMapper
  mapper.registerModule(DefaultScalaModule)

  def getSignature(sensor: String, value: Double, anomaly: Int): Array[Byte] = {
    mac.reset()
    mac.doFinal(Entry(sensor, value, anomaly, Array[Byte]()).toBytes)
  }

  def create(sensor: String, value: Double, anomaly: Int): Entry =
    Entry(sensor, value, anomaly, getSignature(sensor, value, anomaly))

  def get(bytes: Array[Byte]): Entry = {
    val entry = mapper.readValue[Entry](bytes)
    val signature = getSignature(entry.sensor, entry.value, entry.anomaly)
    if (!signature.sameElements(entry.signature)) {
      throw new SecurityException("Entry signature doesn't match")
    }
    entry
  }
}

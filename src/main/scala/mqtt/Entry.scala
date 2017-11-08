package mqtt

import java.io._

final case class Entry(sensor: String, value: Double, anomaly: Int) extends Serializable

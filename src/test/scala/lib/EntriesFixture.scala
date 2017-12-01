package lib

object EntriesFixture {
  val Precision = 0.0002

  def apply(): EntriesFixture = new EntriesFixture()
}

class EntriesFixture {
  // Declare the class to get better visibility on the data
  case class Row(sensor: String, ts: String, value: Double, anomaly: Int)

  private def getData = {
    // Read the values from the CSV file
    val iter = scala.io.Source.fromResource("entries.csv").getLines

    // Get the data
    val l = iter.map(_.split(",") match {
      case Array(a, b, c, d) => Row(a, b, c.toDouble, d.toInt)
    }).toList

    // Get the sensor name for further analysis
    val name = l.head.sensor

    // Features are multi-dimensional, labels are integers
    val mapping = (x: Row) => (Array(x.value), x.anomaly)

    // Extract the features and the labels for the given sensor
    l.filter(_.sensor == name).map(mapping).unzip
  }

  val (features, labels) = getData
}

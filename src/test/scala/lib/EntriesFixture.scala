package lib

import smile.data._
import smile.data.formula._
import smile.data.`type`._

import scala.jdk.CollectionConverters._

object EntriesFixture {
  val Precision = 0.0002

  def apply(): EntriesFixture = new EntriesFixture()
}

class EntriesFixture {
  // Declare the class to get better visibility on the data
  case class Row(sensor: String, ts: String, value: Double, anomaly: Int)

  private def getData = {
    // Read the values from the CSV file
    val iter = scala.io.Source.fromResource("entries.csv").getLines()

    // Get the data
    val l = iter.map(_.split(",") match {
      case Array(a, b, c, d) => Row(a, b, c.toDouble, d.toInt)
    }).toList

    // Get the sensor name for further analysis
    val name = l.head.sensor

    val data = DataFrame.of(
      l.filter(_.sensor == name)
        .map(row => Tuple.of(
          Array(
            row.value.asInstanceOf[AnyRef],
            row.anomaly.asInstanceOf[AnyRef]),
          DataTypes.struct(
            new StructField("value", DataTypes.DoubleType),
            new StructField("anomaly", DataTypes.IntegerType))))
          .asJava)
    val formula = "anomaly" ~ "value"
    (data, formula)
  }

  val (data, formula) = getData
}

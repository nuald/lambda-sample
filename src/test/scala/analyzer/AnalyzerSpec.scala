package analyzer

import akka.event.LoggingAdapter
import org.scalatest._

import scala.language.postfixOps
import lib.Common.using
import java.io._

import lib.BinarySerializer
import smile.classification.{RandomForest, randomForest}

class AnalyzerSpec extends FlatSpec with Matchers {
  implicit val logger: LoggingAdapter = akka.event.NoLogging

  class Fixture {
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

  def fixture = new Fixture

  "The analysis process" should "run the fast analysis correctly" in {
    val f = fixture

    // Get the first 200 values
    val values = f.features.flatten.take(200)

    // Use the fast analyzer for the sample values
    val samples = Seq(10, 200, -100)
    samples.map(sample => analyzer.Analyzer.withHeuristic(sample, values)) match {
      case Seq(notAnomaly, anomaly, risky) =>
        notAnomaly should be (0)
        anomaly should be (1)
        risky should be (0.5 +- 0.5)
    }
  }

  it should "run the full analysis correctly" in {
    val f = fixture

    // Fit the model
    val rf = randomForest(f.features.toArray, f.labels.toArray)

    // Use the full analyzer for the sample values
    val samples = Seq(10, 200, -100)
    samples.map(sample => analyzer.Analyzer.withTrainedModel(sample, rf)) match {
      case Seq(notAnomaly, anomaly, risky) =>
        notAnomaly should be (0.1 +- 0.1)
        anomaly should be (0.9 +- 0.1)
        risky should be (0.5 +- 0.5)
    }
  }

  it should "run the REPL full analysis correctly" in {
    val f = fixture

    // Fit the model
    val originalRf = randomForest(f.features.toArray, f.labels.toArray)

    // Set up the implicit for the usage() function
    implicit val logger: LoggingAdapter = akka.event.NoLogging

    // Serialize the model
    val model = using(new ByteArrayOutputStream())(_.close) { ostream =>
      using(new ObjectOutputStream(ostream))(_.close) { out =>
        out.writeObject(originalRf)
      }
      ostream.toByteArray
    }

    val futureRf = using(new ObjectInputStream(
      new ByteArrayInputStream(model.get))
    )(_.close) { in =>
      in.readObject().asInstanceOf[RandomForest]
    }
    val rf = futureRf.get

    // Use the loaded model for the sample values
    val samples = Seq(10, 200, -100)
    samples.map { sample =>
      val probability = new Array[Double](2)
      val prediction = rf.predict(Array(sample), probability)
      (prediction, probability)
    } match {
      case Seq(notAnomaly, anomaly, risky) =>
        notAnomaly._1 should be (0)
        anomaly._1 should be (1)
        risky._1 should be (1)
    }
  }

  it should "serialize the model correctly" in {
    val f = fixture
    val serializer = new BinarySerializer()

    // Fit the model
    val rf = randomForest(f.features.toArray, f.labels.toArray)
    val originalBytes = using(new ByteArrayOutputStream())(_.close) { ostream =>
      using(new ObjectOutputStream(ostream))(_.close) { out =>
        out.writeObject(rf)
      }
      ostream.toByteArray
    }

    val bytes = serializer.toBinary(rf)
    val deserializedRf = serializer.fromBinary(
      bytes,
      BinarySerializer.RandomForestManifest
    ).asInstanceOf[RandomForest]
    val deserializedBytes = using(new ByteArrayOutputStream())(_.close) { ostream =>
      using(new ObjectOutputStream(ostream))(_.close) { out =>
        out.writeObject(deserializedRf)
      }
      ostream.toByteArray
    }
    originalBytes.get should contain theSameElementsInOrderAs deserializedBytes.get
  }
}

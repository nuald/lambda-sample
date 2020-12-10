package analyzer

import akka.event.LoggingAdapter

import java.io._
import lib.Common.using
import lib.{BinarySerializer, EntriesFixture}

import org.scalatest._
import matchers.should._

import scala.language.postfixOps
import scala.jdk.CollectionConverters._

import smile.classification.{RandomForest, randomForest}
import smile.data.Tuple
import smile.data.`type`._

class AnalyzerSpec extends flatspec.AnyFlatSpec with Matchers {
  implicit val logger: LoggingAdapter = akka.event.NoLogging

  private[this] def fixture = EntriesFixture()

  "The analysis process" should "run the fast analysis correctly" in {
    val f = fixture

    // Get the first 200 values
    val values = f.data.stream()
      .limit(200).map(_.getDouble(0)).iterator().asScala.toArray

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
    val rf = randomForest(f.formula, f.data)

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
    val originalRf = randomForest(f.formula, f.data)

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
    val samples = Seq(10.0, 200.0, -100.0)
    samples.map { sample =>
      val posteriori = new Array[Double](2)
      val prediction = rf.predict(
        Tuple.of(
          Array(sample),
          DataTypes.struct(
            new StructField("value", DataTypes.DoubleType))),
        posteriori)
      (prediction, posteriori)
    } match {
      case Seq(notAnomaly, anomaly, risky) =>
        notAnomaly._1 should be (0)
        anomaly._1 should be (1)
        risky._1 should be (1)
    }
  }
}

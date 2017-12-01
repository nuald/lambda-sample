package analyzer

import akka.event.LoggingAdapter
import lib.EntriesFixture
import lib.EntriesFixture.Precision
import org.scalatest.{FlatSpec, Matchers}
import smile.classification.randomForest

class MLSpec extends FlatSpec with Matchers {
  implicit val logger: LoggingAdapter = akka.event.NoLogging

  private[this] def fixture = EntriesFixture()

  "The ML wrapper" should "predict with the same probability as origin" in {
    val f = fixture

    // Fit the model
    val rf = randomForest(f.features.toArray, f.labels.toArray)
    val mlRf = ML.RandomForest(rf)

    val samples = Seq(10, 200, -100)
    samples.map { sample =>
      val probability = new Array[Double](2)
      val sampleArray = Array(sample.toDouble)
      rf.predict(sampleArray, probability)
      val mlProbability = mlRf.predict(sampleArray)
      (probability(1), mlProbability(1))
    } match {
      case Seq(notAnomaly, anomaly, risky) =>
        notAnomaly._1 should be (notAnomaly._2 +- Precision)
        anomaly._1 should be (anomaly._2 +- Precision)
        risky._1 should be (risky._2 +- Precision)
    }
  }
}

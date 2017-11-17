# A Sample of Lambda architecture project

The boilerplate project for detecting IoT sensor anomalies using the Lambda architecture.

The system layers:

 - Speed: based on the standard deviation
 - Batch: Random Forest classification (using [Smile](https://haifengl.github.io/smile/) engine)
 - Serving: [Akka](https://akka.io/) cluster with in-memory Redis database and persistence with Cassandra

The data flow:

 1. MQTT messages are produced by the IoT emulator ([Producer](src/main/scala/mqtt/Producer.scala) actor)
 2. MQTT subscriber saves the messages into Cassandra database ([Consumer](src/main/scala/mqtt/Consumer.scala) actor)
 3. The Random Forest model is constantly trained by the messages ([Trainer](src/main/scala/analyzer/Trainer.scala) actor)
 4. HTTP endpoint requests the computation using the trained model and heuristics ([Analyzer](src/main/scala/analyzer/Analyzer.scala) actor)

# Table of Contents

* [A Sample of Lambda architecture project](#a-sample-of-lambda-architecture-project)
  * [Requirements](#requirements)
    * [Cluster client requirements](#cluster-client-requirements)
  * [Usage](#usage)
    * [IoT emulation](#iot-emulation)
    * [Interactive processing](#interactive-processing)
      * [Preparing the data set](#preparing-the-data-set)
      * [Fast analysis](#fast-analysis)
      * [Fitting the model](#fitting-the-model)
      * [Using the model](#using-the-model)
    * [Processing Cluster](#processing-cluster)
      * [Performance testing](#performance-testing)

## Requirements

Please install:

 - [Eclipse Mosquitto](https://mosquitto.org/) MQTT broker
 - [Apache Cassandra](http://cassandra.apache.org/) NoSQL database
 - [Redis](https://redis.io/) in-memory data store
 - [SBT](http://www.scala-sbt.org/) build tool

Optionally you may install:

 - [Graphviz](http://www.graphviz.org/) visualization software (its dot utility is used for
 the Decision Tree visualization in the sample REPL session)
 - [Hey](https://github.com/rakyll/hey) HTTP load generator (used for the performance tests)
 - [Scala](https://www.scala-lang.org/download/) shell (used for [the helper script](start.sc) for clustering)

### Cluster client requirements

The cluster clients only uses Scala shell and SBT. To verify the installations, please use:

    $ sbt compile
    $ scala start.sc client --dry-run

It should create the target jar (`target/scala-2.12` directory) and configurations (`target/env` directory).

## Usage

Configure the Cassandra data store:

    $ cqlsh -f resources/cassandra/schema.sql

*NOTE: For dropping the keyspace please use: `$ cqlsh -e "drop keyspace sandbox;"`.*

Run the servers:

    $ mosquitto
    $ cassandra -f
    $ redis-server

Run the system (for the convenience, all microservices are packaged into the one system):

    $ sbt run

Please note that Random Forest classification requires at least two classes in the input
data (that means that the analyzed messages should contain anomalies). Please use the
MQTT producer described below to generate anomalies,
otherwise the [Trainer](src/main/scala/analyzer/Trainer.scala) shows the errors.

### IoT emulation

Modify the sensor values with the Producer: http://localhost:8081

![](resources/img/producer.png?raw=true)

Verify the messages by subscribing to the required MQTT topic:

    $ mosquitto_sub -t sensors/power

### Interactive processing

Verify the data stores with the Dashboard: http://localhost:8080

![](resources/img/timeline.png?raw=true)

Verify the entries data store using CQL:

    $ cqlsh -e "select * from sandbox.entry limit 10;"

Dump the entries into the CSV file:

    $ cqlsh -e "copy sandbox.entry(sensor,ts,value,anomaly) to 'list.csv';"

An example REPL session (with `sbt console`) consists of 4 parts:

1. Preparing the data set
2. Fast analysis
3. Fitting the model (full analysis)
4. Using the model for the prediction

#### Preparing the data set

Read the CSV file and extract the features and the labels for the particular sensor:

```scala
// Fix the borked REPL
jline.TerminalFactory.get.init

// Declare the class to get better visibility on the data
case class Row(sensor: String, ts: String, value: Double, anomaly: Int)

// Read the values from the CSV file
val iter = scala.io.Source.fromFile("list.csv").getLines

// Get the data
val l = iter.map(_.split(",") match {
  case Array(a, b, c, d) => Row(a, b, c.toDouble, d.toInt)
}).toList

// Get the sensor name for further analysis
val name = l.head.sensor

// Features are multi-dimensional, labels are integers
val mapping = (x: Row) => (Array(x.value), x.anomaly)

// Extract the features and the labels for the given sensor
val (features, labels) = l.filter(_.sensor == name).map(mapping).unzip

```

#### Fast analysis

Fast analysis (labels are ignored because we don't use any training here):

```scala
// Get the first 200 values
val values = features.flatten.take(200)

// Use the fast analyzer for the sample values
val samples = Seq(10, 200, -100)
samples.map(sample => analyzer.FastAnalyzer.getAnomaly(sample, values))

```

#### Fitting the model

Fit and save the Random Forest model:

```scala
import scala.language.postfixOps

import lib.Common.using
import java.io._
import scala.sys.process._
import smile.classification.randomForest

// Fit the model
val rf = randomForest(features.toArray, labels.toArray)

// Get the dot diagram for a sample tree
val desc = rf.getTrees()(0).dot

// View the diagram (macOS example)
s"echo $desc" #| "dot -Tpng" #| "open -a Preview -f" !

// Set up the implicit for the using() function
implicit val logger = akka.event.NoLogging

// Serialize the model
using(new ObjectOutputStream(new FileOutputStream("target/rf.bin")))(_.close) { out =>
  out.writeObject(rf)
}

```

#### Using the model

Load and use the model:

```scala
// Fix the borked REPL
jline.TerminalFactory.get.init

// Set up the implicit for the using() function
implicit val logger = akka.event.NoLogging

import lib.Common.using
import java.io._
import smile.classification.RandomForest

// Deserialize the model
val futureRf = using(new ObjectInputStream(new FileInputStream("target/rf.bin")))(_.close) { in =>
  in.readObject().asInstanceOf[RandomForest]
}
val rf = futureRf.get

// Use the loaded model for the sample values
val samples = Seq(10, 200, -100)
samples.map { sample =>
  val probability = new Array[Double](2)
  val prediction = rf.predict(Array(sample), probability)
  (prediction, probability)
}

```

### Processing Cluster

Verify the endpoint for anomaly detection:

    $ curl http://localhost:8082/

Check the latest analyzer snapshots:

    $ redis-cli hgetall fast-analysis
    $ redis-cli hgetall full-analysis

*NOTE: For deleting the shapshot please use: `$ redis-cli del fast-analysis full-analysis`.*

Verify the history of detecting anomalies using CQL:

    $ cqlsh -e "select * from sandbox.analysis limit 10;"

Run the servers (please use the external IP):

    $ scala start.sc server --server-host=<host>

Run the client (please use the external IP):

    $ scala start.sc client --server-host=<server-host> --client-host=<client-host> --client-port=<port>

#### Performance testing

By default, the server runs its own analyzer, however, it may affect
the metrics due to local analyzer works much faster than the remote ones.
To normalize the metrics you may use the `--no-local-analyzer` option: 

    $ scala start.sc server --server-host=<host> --no-local-analyzer

The endpoint supports two modes for analyzing - the usual one and the stress-mode compatible.
The latter one is required to eliminate the side-effects of connectivity issues to Cassandra
and Redis (analyzers use the cached results instead of fetching the new data and recalculating):

    $ curl http://localhost:8082/stress

To manually get the performance metrics please use the hey tool:

    $ hey -n 500 -c 10 -t 10 http://127.0.0.1:8082/
    $ hey -n 500 -c 10 -t 10 http://127.0.0.1:8082/stress

Otherwise the stats is available via the Dashboard: http://localhost:8080

![](resources/img/performance.png?raw=true)

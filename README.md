# A Sample of Lambda architecture project

The boilerplate project for detecting IoT sensor anomalies using the Lambda architecture.

# Table of Contents

* [A Sample of Lambda architecture project](#a-sample-of-lambda-architecture-project)
  * [Requirements](#requirements)
  * [Usage](#usage)
    * [IoT emulation](#iot-emulation)
    * [Interactive processing](#interactive-processing)
      * [Preparing the data set](#preparing-the-data-set)
      * [Fast analysis](#fast-analysis)
      * [Fitting the model](#fitting-the-model)
      * [Using the model](#using-the-model)
    * [Processing Cluster](#processing-cluster)

## Requirements

Please install:

 - [Eclipse Mosquitto](https://mosquitto.org/) MQTT broker
 - [Apache Cassandra](http://cassandra.apache.org/) NoSQL database
 - [Redis](https://redis.io/) in-memory data store
 - [SBT](http://www.scala-sbt.org/) build tool

## Usage

Configure the Cassandra data store:

    $ cqlsh -f resources/cql/schema.sql

*NOTE: For dropping the keyspace please use: `$ cqlsh -e "drop keyspace sandbox;"`.*

Run the servers:

    $ mosquitto
    $ cassandra -f
    $ redis-server

Run the system (for the convenience, all microservices are packaged into the one system):

    $ sbt run

### IoT emulation

Modify the sensor values with the Producer: http://localhost:8081

Verify the messages by subscribing to the required MQTT topic:

    $ mosquitto_sub -t sensors/power

### Interactive processing

Verify the data stores with the Dashboard: http://localhost:8080

Verify the entries data store using CQL:

    $ cqlsh -e "select * from sandbox.entry limit 10;"

Dump the entries into the CSV file:

    $ cqlsh -e "copy sandbox.entry(sensor,ts,value,anomaly) to 'list.csv' with header=true;"

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

// Read the values from the CSV file
val iter = scala.io.Source.fromFile("list.csv").getLines

// Get the header
val header = iter.next.split(",")

// Get the data
val l = iter.map(_.split(",")).toList

// Get the sensor name for further analysis
val name = l.head(header.indexOf("sensor"))

// Features are multi-dimensional, labels are integers
val mapping = (x: Array[String]) => (Array(x(2).toDouble), x(3).toInt)

// Extract the features and the labels for the given sensor
val (features, labels) = l.filter(_(0) == name).map(mapping).unzip
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
import java.io._
import smile.classification.randomForest

// Fit the model
val rf = randomForest(features.toArray, labels.toArray)

// Get the dot format for a sample tree (could be visualized with http://viz-js.com/)
rf.getTrees()(0).dot

// Serialize the model
val fileOut = new FileOutputStream("rf.bin")
val out = new ObjectOutputStream(fileOut)
out.writeObject(rf)
out.close
fileOut.close
```

#### Using the model

Load and use the model:

```scala
import java.io._
import smile.classification.RandomForest

// Deserialize the model
val fileIn = new FileInputStream("rf.bin")
val in = new ObjectInputStream(fileIn)
val rf = in.readObject().asInstanceOf[RandomForest]
in.close
fileIn.close

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

Check the latest analyzer snapshot:

    $ redis-cli hgetall fast-analysis

Verify the history of detecting anomalies using CQL:

    $ cqlsh -e "select * from sandbox.analysis limit 10;"

# A Sample of Lambda Architecture Project

The boilerplate project for detecting IoT sensor anomalies using the Lambda architecture.

## Requirements

Please install:

 - [Eclipse Mosquitto](https://mosquitto.org/) MQTT broker
 - [Apache Cassandra](http://cassandra.apache.org/) NoSQL database
 - [Redis](https://redis.io/) in-memory data store
 - [Apache Spark](https://spark.apache.org/) data processing engine
 - [SBT](http://www.scala-sbt.org/) build tool

## Usage

Configure the Cassandra data store:

    $ cqlsh -f resources/cql/schema.sql

*NOTE: For dropping the keyspace please use: `$ cqlsh -e "drop keyspace sandbox;"`*

Run the servers:

    $ mosquitto
    $ cassandra -f
    $ redis-server

Run the system (for the convenience, all microservices are packaged into the one system):

    $ sbt run

### IoT Emulation

Modify the sensor values with the Producer: http://localhost:8081

Verify the messages by subscribing to the required MQTT topic:

    $ mosquitto_sub -t sensors/power

### Interactive Processing

Verify the data stores with the Dashboard: http://localhost:8080

Verify the entries data store using CQL:

    $ cqlsh -e "select * from sandbox.entry limit 10;"

Dump the entries into the CSV file:

    $ cqlsh -e "copy sandbox.entry(sensor,ts,value,anomaly) to 'list.csv' with header=true;"

An example REPL session with `sbt console`:

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

// Get the first 200 values for the given sensor
val values = l.filter(_(0) == name).take(200).map(_(2).toDouble)

// Use the fast analyzer for the sample values
val samples = Seq(10, 200, -100)
samples.map(sample => analyzer.FastAnalyzer.getAnomaly(sample, values))
```

An example REPL session for the binomial logistic regression analysis with `spark-shell`:

```scala
// Necessary imports
import org.apache.spark.ml.classification.DecisionTreeClassifier
import org.apache.spark.ml.feature.VectorAssembler

// Create the assembler to generate features vector
val assembler = new VectorAssembler().setInputCols(Array("value")).setOutputCol("features")

// Read the values from the CSV file
val l = spark.read.options(Map("header" -> "true", "inferSchema" -> "true")).csv("list.csv")

// Get the sensor name for further analysis
val sensorCol = l.schema.fieldIndex("sensor")
val name = l.first.getString(sensorCol)

// Get the first 1000 values for the given sensor
val filtered = l.filter(row => row.getString(sensorCol) == name).limit(1000)

// Create the model
val lr = new DecisionTreeClassifier().setLabelCol("anomaly")

// Fit the model
val model = lr.fit(assembler.transform(filtered))

// Prepare test data (the model ignores label value, can use any)
val samples = Seq(10, 200, -100)
val seq = samples.map(sample => (0.0, sample))
val t = spark.createDataFrame(seq).toDF("anomaly", "value")

// Makes the prediction
val predictions = model.transform(assembler.transform(t))

// Show the probabilities
predictions.select("probability", "prediction").show(false)

```

### Processing Cluster

Verify the endpoint for anomaly detection:

    $ curl http://localhost:8082/

Check the latest analyzer snapshot:

    $ redis-cli hgetall fast-analysis

Verify the history of detecting anomalies using CQL:

    $ cqlsh -e "select * from sandbox.analysis limit 10;"

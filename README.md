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

    $ cqlsh -k sandbox -e "select * from entry limit 10;"

Dump the entries into the CSV file:

    $ cqlsh -k sandbox -e "copy entry(sensor,ts,value) to 'list.csv';"

An example REPL session with `sbt console`:

```scala
// Fix the borked REPL
jline.TerminalFactory.get.init

// Read the values from the CSV file
val l = scala.io.Source.fromFile("list.csv").getLines.map(_.split(",")).toList

// Get the sensor name for further analysis
val name = l(0)(0)

// Get the first 200 values for the given sensor
val values = l.filter(_(0) == name).map(_(2).toDouble).take(200)

// Use the fast analyzer for the sample value
analyzer.FastAnalyzer.getAnomaly(99, values)
```

### Processing Cluster

Verify the endpoint for anomaly detection:

    $ curl http://localhost:8082/

Check the latest analyzer snapshot:

    $ redis-cli hgetall fast-analysis

Verify the history of detecting anomalies using CQL:

    $ cqlsh -k sandbox -e "select * from analysis limit 10;"

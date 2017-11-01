# A Sample of Lambda Architecture Project

The boilerplate project for detecting IoT sensor anomalies using the Lambda architecture.

## Requirements

Please install:

 - [Eclipse Mosquitto](https://mosquitto.org/) MQTT broker
 - [Apache Cassandra](http://cassandra.apache.org/) NoSQL database
 - [Apache Spark](https://spark.apache.org/) data processing engine
 - [SBT](http://www.scala-sbt.org/) build tool

Configure the Cassandra data store:

    $ cqlsh -f resources/cql/schema.cql

## IoT Emulation

Run the MQTT server:

    $ mosquitto

Generate the messages:

    $ sbt "runMain mqtt.Producer"

Verify the messages by subscribing to the required MQTT topic:

    $ mosquitto_sub -t sensors/power

## Processing Cluster

Run Cassandra:

    $ cassandra -f

Subscribe to the required MQTT topic and put the messages into the Cassandra data store:

    $ sbt "runMain mqtt.Consumer"

Verify the data store using CQL:

    $ cqlsh -k sandbox -e "select * from entry limit 10;"

Verify the data store with the [dashboard](http://localhost:8080):

    $ sbt "runMain dashboard.WebServer"

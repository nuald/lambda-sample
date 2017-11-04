# A Sample of Lambda Architecture Project

The boilerplate project for detecting IoT sensor anomalies using the Lambda architecture.

## Requirements

Please install:

 - [Eclipse Mosquitto](https://mosquitto.org/) MQTT broker
 - [Apache Cassandra](http://cassandra.apache.org/) NoSQL database
 - [Apache Spark](https://spark.apache.org/) data processing engine
 - [SBT](http://www.scala-sbt.org/) build tool

## Usage

Configure the Cassandra data store:

    $ cqlsh -f resources/cql/schema.cql

*NOTE: For dropping the keyspace please use: `$ cqlsh -e "drop keyspace sandbox;"`*

Run the servers:

    $ mosquitto
    $ cassandra -f

Run the system (for the convenience, all the microservices are packaged into one system):

    $ sbt run

### IoT Emulation

Modify the sensor values with the Producer: http://localhost:8081

Verify the messages by subscribing to the required MQTT topic:

    $ mosquitto_sub -t sensors/power

### Processing Cluster

Verify the data store using CQL:

    $ cqlsh -k sandbox -e "select * from entry limit 10;"

Verify the data store with the Dashboard: http://localhost:8080

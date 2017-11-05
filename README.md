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

### Processing Cluster

Verify the data stores with the Dashboard: http://localhost:8080

Verify the entries data store using CQL:

    $ cqlsh -k sandbox -e "select * from entry limit 10;"

Verify the endpoint for anomaly detection:

    $ curl http://localhost:8082/

Check the latest analyzer snapshot:

    $ redis-cli hgetall fast-analysis 

Verify the history of detecting anomalies using CQL:

    $ cqlsh -k sandbox -e "select * from analysis limit 10;"

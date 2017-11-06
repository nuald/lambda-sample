CREATE KEYSPACE IF NOT EXISTS sandbox
WITH replication = {'class':'SimpleStrategy', 'replication_factor' : 1};

CREATE TABLE IF NOT EXISTS sandbox.entry(
  sensor text,
  ts timestamp,
  value double,
  anomaly int,
  PRIMARY KEY ((sensor), ts)
) WITH CLUSTERING ORDER BY (ts DESC);

CREATE TABLE IF NOT EXISTS sandbox.analysis(
  sensor text,
  ts timestamp,
  anomaly double,
  PRIMARY KEY ((sensor), ts)
) WITH CLUSTERING ORDER BY (ts DESC);

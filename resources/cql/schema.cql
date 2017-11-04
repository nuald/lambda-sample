CREATE KEYSPACE IF NOT EXISTS sandbox
WITH replication = {'class':'SimpleStrategy', 'replication_factor' : 1};

CREATE TABLE IF NOT EXISTS sandbox.entry(
  sensor text,
  ts timestamp,
  value int,
  anomaly boolean,
  PRIMARY KEY ((sensor), ts)
) WITH CLUSTERING ORDER BY (ts DESC);

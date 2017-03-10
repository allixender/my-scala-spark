#!/bin/bash


echo "SPARK_HOME: $SPARK_HOME"

# bin/spark-class org.apache.spark.deploy.worker.Worker spark://127.0.0.1:7077
# bin/spark-shell --master spark://127.0.0.1:7077 --conf spark.akka.frameSize=200 --conf spark.cassandra.connection.host=127.0.0.1 --jars /home/akmoch/dev/build/my-scala-spark/target/scala-2.10/my-spark-cassandra-assembly-1.0-SNAPSHOT.jar

cd $SPARK_HOME

bin/spark-submit --master spark://127.0.0.1:7077 --class my.georef.GeoRefXml --conf spark.cassandra.connection.host=127.0.0.1 /home/akmoch/dev/build/my-scala-spark/target/scala-2.11/georef-spark-cassandra-assembly-1.0-SNAPSHOT.jar


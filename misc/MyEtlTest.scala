import geotrellis.spark._
import geotrellis.spark.etl.Etl
import geotrellis.spark.etl.cassandra._
import geotrellis.spark.etl._
import geotrellis.spark.etl.hadoop.HadoopModule
import geotrellis.spark.etl.s3.S3Module
import geotrellis.spark.io.index.ZCurveKeyIndexMethod
import geotrellis.spark.utils.SparkUtils
import geotrellis.spark.op.local._

object GeoTrellisETL extends App {
  val etl = Etl[SpatialKey](args, CassandraModule, HadoopModule)

  implicit val sc = SparkUtils.createSparkContext("GeoTrellis ETL")
  val (id, rdd) = etl.load()
  val result = rdd.localAdd(1)
  etl.save(id, result, ZCurveKeyIndexMethod)
  sc.stop()
}

import com.typesafe.scalalogging.slf4j.LazyLogging
import geotrellis.raster.io.geotiff.{SingleBandGeoTiff, Tags, GeoTiffOptions}
import geotrellis.spark._
import geotrellis.spark.etl.Etl
import geotrellis.spark.etl.cassandra._
import geotrellis.spark.etl._
import geotrellis.spark.etl.hadoop.HadoopModule
import geotrellis.spark.etl.s3.S3Module
import geotrellis.spark.io.cassandra.{CassandraLayerMetaData, CassandraRasterCatalog, Cassandra}
import geotrellis.spark.io.index.ZCurveKeyIndexMethod
import geotrellis.spark.tiling.ZoomedLayoutScheme
import geotrellis.spark.utils.SparkUtils
import geotrellis.spark.op.local._
import geotrellis.vector.Polygon
import geotrellis.vector.io.json.GeoJsonSupport

import java.nio.file.{Paths, Files}
import java.nio.charset.StandardCharsets

object Timer extends LazyLogging {
  // Time how long a block takes to execute.  From here:
  // http://stackoverflow.com/questions/9160001/how-to-profile-methods-in-scala
  def timedTask[R](msg: String)(block: => R): R = {
    val t0 = System.currentTimeMillis
    val result = block    // call-by-name
    val t1 = System.currentTimeMillis
    println(msg + " in " + ((t1 - t0)) + " ms"    )
    logger.info(msg + " in " + ((t1 - t0)) + " ms")
    result
  }

  def timedTask[R](msg: String, reporter: String => Unit)(block: => R): R = {
    val t0 = System.currentTimeMillis
    val result = block    // call-by-name
    val t1 = System.currentTimeMillis
    reporter(msg + " in " + ((t1 - t0)) + " ms")
    result
  }
}

object Extents2 extends GeoJsonSupport {
  import spray.json._
  val extents = Map[String, Polygon](
    "one_1_1" ->
      """{"type":"Feature","properties":{"name":1},"geometry":{
        "type":"Polygon",
        "coordinates":[[
          [-116.4916666700750199,41.3999999930650020],
          [-116.4916666700750199,45.6666666580250009],
          [-120.7583333350350188,45.6666666580250009],
          [-120.7583333350350188,41.3999999930650020],
          [-116.4916666700750199,41.3999999930650020]]]}}""".parseJson.convertTo[Polygon],
    "one_2_1" ->
      """{"type":"Feature","properties":{"name":2},"geometry":{
        "type":"Polygon",
        "coordinates":[[
          [-116.4916666700750199,37.1333333281050031],
          [-116.4916666700750199,41.3999999930650020],
          [-120.7583333350350188,41.3999999930650020],
          [-120.7583333350350188,37.1333333281050031],
          [-116.4916666700750199,37.1333333281050031]]]}}""".parseJson.convertTo[Polygon],
    "one_1_2" ->
      """{"type":"Feature","properties":{"name":3},"geometry":{
        "type":"Polygon",
        "coordinates":[[
          [-112.2250000051150209,41.3999999930650020],
          [-112.2250000051150209,45.6666666580250009],
          [-116.4916666700750199,45.6666666580250009],
          [-116.4916666700750199,41.3999999930650020],
          [-112.2250000051150209,41.3999999930650020]]]}}""".parseJson.convertTo[Polygon],
    "one_2_2" ->
      """{"type":"Feature","properties":{"name":4},"geometry":{
        "type":"Polygon",
        "coordinates":[[
          [-112.2250000051150209,37.1333333281050031],
          [-112.2250000051150209,41.3999999930650020],
          [-116.4916666700750199,41.3999999930650020],
          [-116.4916666700750199,37.1333333281050031],
          [-112.2250000051150209,37.1333333281050031]]]}}""".parseJson.convertTo[Polygon],
    "one_all4" ->
      """{"type":"Feature","properties":{"name":5},"geometry":{
        "type":"Polygon",
        "coordinates":[[
          [-112.2250000051150209,37.1333333281050031],
          [-112.2250000051150209,45.6666666580250009],
          [-120.7583333350350188,45.6666666580250009],
          [-120.7583333350350188,37.1333333281050031],
          [-112.2250000051150209,37.1333333281050031]]]}}""".parseJson.convertTo[Polygon],
    "one_1_1_smaller" ->
      """{"type":"Feature","properties":{"name":7},"geometry":{
        "type":"Polygon",
        "coordinates":[[
          [-117.0,42.0],
          [-117.0,45.0],
          [-120.0,45.0],
          [-120.0,42.0],
          [-117.0,42.0]]]}}""".parseJson.convertTo[Polygon],
    "horowhenua" ->
      """{"type":"Feature","properties":{"name":8},"geometry":{
        "type":"Polygon",
        "coordinates":[[
          [175.1290892754978,-40.7546239708482],
          [175.1290892754978,-40.4702916157891],
          [175.4042099991834,-40.4702916157891],
          [175.4042099991834,-40.7546239708482],
          [175.1290892754978,-40.7546239708482]]]}}""".parseJson.convertTo[Polygon]
  )
}

object GeoCasTiff2 extends App {

  System.setProperty("com.sun.media.jai.disableMediaLib", "true")
  implicit val sparkContext = SparkUtils.createSparkContext("CasGeoTiffAlex2")
  val sparkConf = sparkContext.getConf
  val hadoopConf = sparkContext.hadoopConfiguration
  println("spark.cassandra.connection.host " + sparkConf.get("spark.cassandra.connection.host"))

  Cassandra.withSession("127.0.0.1", "gtcas") { implicit session =>
    val lname = "onetiles84"
    val catalog = CassandraRasterCatalog("attributes")
    val layoutScheme = ZoomedLayoutScheme(256)

    val layerId_z1 = LayerId(lname, 1)
    val layerId_z2 = LayerId(lname, 2)
    val layerId_z3 = LayerId(lname, 3)
    val layerId_z4 = LayerId(lname, 4)
    val layerId_z5 = LayerId(lname, 5)
    val layerId_z6 = LayerId(lname, 6)
    val layerId_z7 = LayerId(lname, 7)
    val layerId_z8 = LayerId(lname, 8)

    val ext_1_1 = Extents2.extents.get("one_1_1")
    val ext_1_1_smaller = Extents2.extents.get("one_1_1_smaller")
    val ext_all4 = Extents2.extents.get("one_all4")

    val meta1 = catalog.attributeStore.read[CassandraLayerMetaData](layerId_z1, "metadata").rasterMetaData
    val meta2 = catalog.attributeStore.read[CassandraLayerMetaData](layerId_z2, "metadata").rasterMetaData
    val meta3 = catalog.attributeStore.read[CassandraLayerMetaData](layerId_z3, "metadata").rasterMetaData
    val meta4 = catalog.attributeStore.read[CassandraLayerMetaData](layerId_z4, "metadata").rasterMetaData
    val meta5 = catalog.attributeStore.read[CassandraLayerMetaData](layerId_z5, "metadata").rasterMetaData
    val meta6 = catalog.attributeStore.read[CassandraLayerMetaData](layerId_z6, "metadata").rasterMetaData
    val meta7 = catalog.attributeStore.read[CassandraLayerMetaData](layerId_z7, "metadata").rasterMetaData
    val meta8 = catalog.attributeStore.read[CassandraLayerMetaData](layerId_z8, "metadata").rasterMetaData

    val bounds_z1_all = meta1.mapTransform(ext_all4.get.envelope)

    val bounds_z1 = meta1.mapTransform(ext_1_1_smaller.get.envelope)
    val bounds_z2 = meta2.mapTransform(ext_1_1_smaller.get.envelope)
    val bounds_z3 = meta3.mapTransform(ext_1_1_smaller.get.envelope)
    val bounds_z4 = meta4.mapTransform(ext_1_1_smaller.get.envelope)
    val bounds_z5 = meta5.mapTransform(ext_1_1_smaller.get.envelope)
    val bounds_z6 = meta6.mapTransform(ext_1_1_smaller.get.envelope)
    val bounds_z7 = meta7.mapTransform(ext_1_1_smaller.get.envelope)
    val bounds_z8 = meta8.mapTransform(ext_1_1_smaller.get.envelope)

    println(s"polygon bounds_all ${ext_all4.get.toString()}")
    println(s"polygon bounds_11small ${ext_1_1_smaller.get.toString()}")
    println("")
    println(s"bounds_z1_all ${bounds_z1_all}")
    println(s"bounds_z1 ${bounds_z1}")
    println(s"bounds_z2 ${bounds_z2}")
    println(s"bounds_z3 ${bounds_z3}")
    println(s"bounds_z4 ${bounds_z4}")
    println(s"bounds_z5 ${bounds_z5}")
    println(s"bounds_z6 ${bounds_z6}")
    println(s"bounds_z7 ${bounds_z7}")
    println(s"bounds_z8 ${bounds_z8}")
    println("")

    val path = "/home/akmoch/dev/build/tifout2/"

    Timer.timedTask( s"""zoom 1 extent""") {

      val rdd_z1_all : RasterRDD[SpatialKey] = catalog.query[SpatialKey](layerId_z1).where(Intersects(bounds_z1_all)).toRDD
      println(s"rdd_z1_all count ${rdd_z1_all.count()}")
      println("")

      rdd_z1_all.foreach { case (key, tile) =>
        val keyext = meta1.mapTransform(key)
        val extstring = keyext.toPolygon().toString()
        val pathext = key.toString.replace(" ", "_").replace(":", ".").replace("(","").replace(")","").replace(",","_")
        Files.write(Paths.get(path + pathext + ".wkt"), extstring.getBytes(StandardCharsets.UTF_8))
        println(s"rdd_z1_all poly: $pathext => $extstring")
        val geoTiff = SingleBandGeoTiff(tile, keyext, meta1.crs, Tags.empty, GeoTiffOptions.DEFAULT)
        geoTiff.write(path + pathext + ".tif")
      }
    }

    Timer.timedTask( s"""zoom 1 small extent""") {

      val rdd_z1 : RasterRDD[SpatialKey] = catalog.query[SpatialKey](layerId_z1).where(Intersects(bounds_z1)).toRDD
      println(s"rdd_z1 count ${rdd_z1.count()}")
      println("")

      rdd_z1.foreach { case (key, tile) =>
        val keyext = meta1.mapTransform(key)
        val extstring = keyext.toPolygon().toString()
        val pathext = key.toString.replace(" ", "_").replace(":", ".").replace("(","").replace(")","").replace(",","_")
        Files.write(Paths.get(path + "z1/" + pathext + ".wkt"), extstring.getBytes(StandardCharsets.UTF_8))
        println(s"rdd_z1 poly: $pathext => $extstring")
        val geoTiff = SingleBandGeoTiff(tile, keyext, meta1.crs, Tags.empty, GeoTiffOptions.DEFAULT)
        geoTiff.write(path + "z1/" + pathext + ".tif")
      }
    }
    Timer.timedTask( s"""zoom 2 small extent""") {

      val rdd_z2 : RasterRDD[SpatialKey] = catalog.query[SpatialKey](layerId_z2).where(Intersects(bounds_z2)).toRDD
      println(s"rdd_z2 count ${rdd_z2.count()}")
      println("")

      rdd_z2.foreach { case (key, tile) =>
        val keyext = meta2.mapTransform(key)
        val extstring = keyext.toPolygon().toString()
        val pathext = key.toString.replace(" ", "_").replace(":", ".").replace("(","").replace(")","").replace(",","_")
        Files.write(Paths.get(path + "z2/" + pathext + ".wkt"), extstring.getBytes(StandardCharsets.UTF_8))
        println(s"rdd_z2 poly: $pathext => $extstring")
        val geoTiff = SingleBandGeoTiff(tile, keyext, meta1.crs, Tags.empty, GeoTiffOptions.DEFAULT)
        geoTiff.write(path + "z2/" + pathext + ".tif")
      }
    }
    Timer.timedTask( s"""zoom 3 small extent""") {

      val rdd_z3 : RasterRDD[SpatialKey] = catalog.query[SpatialKey](layerId_z3).where(Intersects(bounds_z3)).toRDD
      println(s"rdd_z3 count ${rdd_z3.count()}")
      println("")

      rdd_z3.foreach { case (key, tile) =>
        val keyext = meta3.mapTransform(key)
        val extstring = keyext.toPolygon().toString()
        val pathext = key.toString.replace(" ", "_").replace(":", ".").replace("(","").replace(")","").replace(",","_")
        Files.write(Paths.get(path + "z3/" + pathext + ".wkt"), extstring.getBytes(StandardCharsets.UTF_8))
        println(s"rdd_z3 poly: $pathext => $extstring")
        val geoTiff = SingleBandGeoTiff(tile, keyext, meta1.crs, Tags.empty, GeoTiffOptions.DEFAULT)
        geoTiff.write(path + "z3/" + pathext + ".tif")
      }
    }
    Timer.timedTask( s"""zoom 4 small extent""") {

      val rdd_z4 : RasterRDD[SpatialKey] = catalog.query[SpatialKey](layerId_z4).where(Intersects(bounds_z4)).toRDD
      println(s"rdd_z4 count ${rdd_z4.count()}")
      println("")

      rdd_z4.foreach { case (key, tile) =>
        val keyext = meta4.mapTransform(key)
        val extstring = keyext.toPolygon().toString()
        val pathext = key.toString.replace(" ", "_").replace(":", ".").replace("(","").replace(")","").replace(",","_")
        Files.write(Paths.get(path + "z4/" + pathext + ".wkt"), extstring.getBytes(StandardCharsets.UTF_8))
        println(s"rdd_z4 poly: $pathext => $extstring")
        val geoTiff = SingleBandGeoTiff(tile, keyext, meta1.crs, Tags.empty, GeoTiffOptions.DEFAULT)
        geoTiff.write(path + "z4/" + pathext + ".tif")
      }
    }
    Timer.timedTask( s"""zoom 5 small extent""") {

      val rdd_z5 : RasterRDD[SpatialKey] = catalog.query[SpatialKey](layerId_z5).where(Intersects(bounds_z5)).toRDD
      println(s"rdd_z5 count ${rdd_z5.count()}")
      println("")

      rdd_z5.foreach { case (key, tile) =>
        val keyext = meta5.mapTransform(key)
        val extstring = keyext.toPolygon().toString()
        val pathext = key.toString.replace(" ", "_").replace(":", ".").replace("(","").replace(")","").replace(",","_")
        Files.write(Paths.get(path + "z5/" + pathext + ".wkt"), extstring.getBytes(StandardCharsets.UTF_8))
        println(s"rdd_z5 poly: $pathext => $extstring")
        val geoTiff = SingleBandGeoTiff(tile, keyext, meta1.crs, Tags.empty, GeoTiffOptions.DEFAULT)
        geoTiff.write(path + "z5/" + pathext + ".tif")
      }
    }
    Timer.timedTask( s"""zoom 6 small extent""") {

      val rdd_z6 : RasterRDD[SpatialKey] = catalog.query[SpatialKey](layerId_z6).where(Intersects(bounds_z6)).toRDD
      println(s"rdd_z6 count ${rdd_z6.count()}")
      println("")

      rdd_z6.foreach { case (key, tile) =>
        val keyext = meta6.mapTransform(key)
        val extstring = keyext.toPolygon().toString()
        val pathext = key.toString.replace(" ", "_").replace(":", ".").replace("(","").replace(")","").replace(",","_")
        Files.write(Paths.get(path + "z6/" + pathext + ".wkt"), extstring.getBytes(StandardCharsets.UTF_8))
        println(s"rdd_z6 poly: $pathext => $extstring")
        val geoTiff = SingleBandGeoTiff(tile, keyext, meta1.crs, Tags.empty, GeoTiffOptions.DEFAULT)
        geoTiff.write(path + "z6/" + pathext + ".tif")
      }
    }
    Timer.timedTask( s"""zoom 7 small extent""") {

      val rdd_z7 : RasterRDD[SpatialKey] = catalog.query[SpatialKey](layerId_z7).where(Intersects(bounds_z7)).toRDD
      println(s"rdd_z7 count ${rdd_z7.count()}")
      println("")

      rdd_z7.foreach { case (key, tile) =>
        val keyext = meta7.mapTransform(key)
        val extstring = keyext.toPolygon().toString()
        val pathext = key.toString.replace(" ", "_").replace(":", ".").replace("(","").replace(")","").replace(",","_")
        Files.write(Paths.get(path + "z7/" + pathext + ".wkt"), extstring.getBytes(StandardCharsets.UTF_8))
        println(s"rdd_z7 poly: $pathext => $extstring")
        val geoTiff = SingleBandGeoTiff(tile, keyext, meta1.crs, Tags.empty, GeoTiffOptions.DEFAULT)
        geoTiff.write(path + "z7/" + pathext + ".tif")
      }
    }
    Timer.timedTask( s"""zoom 8 small extent""") {

      val rdd_z8 : RasterRDD[SpatialKey] = catalog.query[SpatialKey](layerId_z8).where(Intersects(bounds_z8)).toRDD
      println(s"rdd_z8 count ${rdd_z8.count()}")
      println("")

      rdd_z8.foreach { case (key, tile) =>
        val keyext = meta8.mapTransform(key)
        val extstring = keyext.toPolygon().toString()
        val pathext = key.toString.replace(" ", "_").replace(":", ".").replace("(","").replace(")","").replace(",","_")
        Files.write(Paths.get(path + "z8/" + pathext + ".wkt"), extstring.getBytes(StandardCharsets.UTF_8))
        println(s"rdd_z8 poly: $pathext => $extstring")
        val geoTiff = SingleBandGeoTiff(tile, keyext, meta1.crs, Tags.empty, GeoTiffOptions.DEFAULT)
        geoTiff.write(path + "z8/" + pathext + ".tif")
      }
    }
  }
}

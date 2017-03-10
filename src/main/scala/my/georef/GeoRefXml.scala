package my.georef

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import com.datastax.spark.connector._
import com.typesafe.scalalogging.LazyLogging
import my.georef.datapreps.{Article, DataLint, GeoName}


object GeoRefXml  extends LazyLogging {

  def main (args: Array[String]): Unit = {

    val conf = new SparkConf(true).set("spark.cassandra.connection.host", "127.0.0.1")
    implicit val sc = new SparkContext("spark://127.0.0.1:7077", "test", conf)

    val JOHNZ = "New Zealand Journal of Hydrology"
    val MARINE = "New Zealand Journal of Marine and Freshwater Research"
    val GEOLOGY = "New Zealand Journal of Geology and Geophysics"

    val geordd = sc.cassandraTable[GeoName]("geo", "linzgeo").collect()
    // val georrdcase = geordd.select("name_id", "name", "crd_datum", "crd_east", "crd_latitude", "crd_longitude",
    //  "crd_north", "crd_projection", "land_district", "status" ).collect()

    val geobc = sc.broadcast(geordd)
    logger.info(s"<><><> geobc ${geobc.value.size}")

    val NUM_PARTITIONS = 2
    val artrdd = sc.cassandraTable("geo", "articles")

    val filtered1 = artrdd.select("articleid", "title", "textabs", "journal").map { row =>
      Article(row.getLong("articleid"), row.getString("title"), row.getString("textabs"), "", row.getString("journal"))
    }

    val count1 = filtered1.count()

    logger.info(s"<><><> start ${count1} articles, planning $NUM_PARTITIONS partitions")

    // stop words and empty filtering
    val filtered2 = DataLint.filterStopWords(filtered1)
    // val filtered3 = DataLint.filterEmptyFullText(filtered2)
    // val forward = sc.parallelize(filtered3.collect()).cache()
    filtered2.cache()
    val filtered2count = filtered2.count()

    logger.info(s"<><><> all articles without stopwords ${filtered2count} articles")


  }

}

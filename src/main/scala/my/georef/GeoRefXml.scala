package my.georef

import org.apache.spark.SparkContext
import org.apache.spark.SparkConf
import com.datastax.spark.connector._
import com.typesafe.scalalogging.LazyLogging
import my.georef.datapreps.{Article, DataLint, GeoName}
import my.georef.ogcxml.MetaData
import scala.util.matching.Regex

object GeoRefXml extends LazyLogging {

  def main(args: Array[String]): Unit = {

    val conf = new SparkConf(true).set("spark.cassandra.connection.host", "127.0.0.1")
    implicit val sc = new SparkContext("spark://127.0.0.1:7077", "test", conf)

    val JOHNZ = "New Zealand Journal of Hydrology"
    val MARINE = "New Zealand Journal of Marine and Freshwater Research"
    val GEOLOGY = "New Zealand Journal of Geology and Geophysics"

    val geordd = sc.cassandraTable[GeoName]("geo", "linzgeo").collect()
    // val georrdcase = geordd.select("name_id", "name", "crd_datum", "crd_east", "crd_latitude", "crd_longitude",
    //  "crd_north", "crd_projection", "land_district", "status" ).collect()

    val geoRegex = geordd.map(geon => (geon, s"\\b(?i)${geon.name}\\b".r))
    val geoRegexBc = sc.broadcast(geoRegex)

    val geobc = sc.broadcast(geordd)
    logger.info(s"<><><> geobc ${geobc.value.size}")
    logger.info(s"<><><> geoRegexBc ${geoRegexBc.value.size}")

    val NUM_PARTITIONS = 2

    val artrdd = sc.cassandraTable[Article]("geo", "articles").collect()
    // val artrdd = sc.cassandraTable[Article]("geo", "articles").take(50)

    val filtered1 = sc.parallelize(artrdd).cache()

    val count1 = filtered1.count()

    logger.info(s"<><><> start ${count1} articles, planning $NUM_PARTITIONS partitions")

    // stop words and empty filtering
    val filtered2 = DataLint.filterStopWords(filtered1).map(art => (art.articleid, art))
    // val filtered3 = DataLint.filterEmptyFullText(filtered2)
    // val forward = sc.parallelize(filtered3.collect()).cache()

    filtered2.cache()

    val filtered2count = filtered2.count()

    logger.info(s"<><><> all articles without stopwords ${filtered2count} articles")

    // geomatch over titles
    //    val articlesTitleMapCS = filtered2.map {
    //      case (articleid, art) =>
    //        val geoList = geoRegexBc.value.filter {
    //          case (georef, regX) =>
    //            val checkOpt = regX.findFirstIn(art.title)
    //            checkOpt.isDefined
    //        }.toList
    //        (articleid, geoList)
    //    }.cache()

    val articlesTitleMapCS = filtered2.mapPartitions {
      iterator =>
        iterator.map {
          case (articleid, art) =>
            val geoList = geoRegexBc.value.filter {
              case (georef, regX) =>
                val checkOpt = regX.findFirstIn(art.title)
                checkOpt.isDefined
            }.toList
            (articleid, geoList)
        }
    }.cache()

    // title geomatch number stats
    val titleGeomatchesCount = articlesTitleMapCS.map { elem =>
      val num = elem._2.size
      num
    }.reduce(_ + _)

    logger.info(s"<><><> titleGeomatchesCount count  ${titleGeomatchesCount}")

    // geomatch over abstracts
    //    val articlesAbstractsMapCS = filtered2.map {
    //      case (articleid, art) =>
    //        val geoList = geoRegexBc.value.filter {
    //          case (georef, regX) =>
    //            val checkOpt = regX.findFirstIn(art.textabs)
    //            checkOpt.isDefined
    //        }.toList
    //        (articleid, geoList)
    //    }.cache()

    val articlesAbstractsMapCS = filtered2.mapPartitions {
      iterator =>
        iterator.map {
          case (articleid, art) =>
            val geoList = geoRegexBc.value.filter {
              case (georef, regX) =>
                val checkOpt = regX.findFirstIn(art.textabs)
                checkOpt.isDefined
            }.toList
            (articleid, geoList)
        }
    }.cache()

    // abstract geomatch number stats
    val abstractsGeomatchesCount = articlesAbstractsMapCS.map { elem =>
      val num = elem._2.size
      num
    }.reduce(_ + _)

    logger.info(s"<><><> abstractsGeomatchesCount count  ${abstractsGeomatchesCount}")

    // REDUCE  TITLE TO COLLECTION FOR CASSANDRA INSERT
    val titleMatchCasCollection = articlesTitleMapCS.map {
      case (articleid, geoList) =>
        val titlematch = geoList.map(geoTuple => geoTuple._1.name_id)
        (articleid, titlematch)
    }.saveToCassandra("geo", "geomatch", SomeColumns("articleid", "titlematch"))

    // REDUCE  ABSTRACT TO COLLECTION FOR CASSANDRA INSERT
    val abstractsMatchCasCollection = articlesAbstractsMapCS.map {
      case (articleid, geoList) =>
        val abstractmatch = geoList.map(geoTuple => geoTuple._1.name_id)
        (articleid, abstractmatch)
    }.saveToCassandra("geo", "geomatch", SomeColumns("articleid", "abstractmatch"))

    val jointMatchesMap = articlesAbstractsMapCS.join(articlesTitleMapCS).map {
      case (articleid, geoListTuple) =>
        val newList1 = geoListTuple._1.map(_._1)
        val newList2 = geoListTuple._2.map(_._1)
        val newList = newList1 ++ newList2
        (articleid, newList)
    }.join(filtered2)

    import scala.collection.JavaConverters._

    val readyXml = jointMatchesMap.mapPartitions {
      iterator =>
        iterator.map {
          case (articleid, dataTuple) =>
            dataTuple match {
              case (geoList, fullArticle) => {
                val tmpMeta = new MetaData(fullArticle)
                val xml = DataLint.enrichGeoRef(geoList, tmpMeta)
                val validateInfo = xml.validate()
                val stringList = validateInfo.asScala.toList
                stringList.foreach(msg => logger.warn(s"<><><> validateInfo  $msg"))
                val xmlText = xml.getMDRecordXml
                (articleid, xmlText)
              }
            }
        }

    }.saveToCassandra("geo", "metaxml", SomeColumns("articleid", "metaxml"))

  }

}

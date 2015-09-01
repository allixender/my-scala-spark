package my.georef

import com.typesafe.scalalogging.slf4j.LazyLogging
import org.apache.spark.{SparkContext, SparkConf}
import com.datastax.spark.connector._


object Algo extends LazyLogging {

  def main (args: Array[String]) {

    val conf = new SparkConf(true).set("spark.cassandra.connection.host", "127.0.0.1")
    implicit val sc = new SparkContext("spark://127.0.0.1:7077", "test", conf)

    val JOHNZ = "New Zealand Journal of Hydrology"
    val MARINE = "New Zealand Journal of Marine and Freshwater Research"
    val GEOLOGY = "New Zealand Journal of Geology and Geophysics"

    val geordd = sc.cassandraTable("geo", "linzgeo")
    val georrdcase = geordd.select("name_id", "name").map { row =>
      GeoName(row.getLong("name_id"), row.getString("name"))
    }

    val geobc = sc.broadcast(georrdcase.collect())
    logger.info(s"<><><> geobc ${geobc.value.size}")

    val NUM_PARTITIONS = 2
    val artrdd = sc.cassandraTable("geo", "articles")

    //*********************************************
    //
    //   JOHNZ RDD
    //
    //*********************************************
    val filtered1 = artrdd.select("articleid", "title", "textabs", "fulltext", "journal").where("journal = ?", JOHNZ).map { row =>
      Article(row.getLong("articleid"), row.getString("title"), row.getString("textabs"), row.getString("fulltext"), row.getString("journal"))
    }

    val count1 = filtered1.count()

    logger.info(s"<><><> start ${count1} articles, planning $NUM_PARTITIONS partitions")

    // stop words and empty filtering
    val filtered2 = DataLint.filterStopWords(filtered1)
    val johnzrdd = DataLint.filterEmptyFullText(filtered2)
    // val forward = sc.parallelize(filtered3.collect()).cache()
    johnzrdd.cache()
    val johnzcount = johnzrdd.count()

    logger.info(s"<><><> all $JOHNZ articles without stopwords ${johnzcount} articles")

    // geomatch over titles
    val johnztitleMapCS = johnzrdd.map { art =>
      val geoList = geobc.value.filter{
        georef =>
          art.title.
            contains(georef.name)
      }.toList
      (art.articleid, geoList)
    }.cache()

    // title word count stats
    val johnztitlewordcount = johnzrdd.map { art =>
      art.title.split(" ").size
    }.reduce(_ + _)

    johnztitleMapCS.saveAsTextFile("/tmp/001_johnz_titlemap")

    // title geomatch number stats
    val johnztitleNum = johnztitleMapCS.map { elem =>
      val num = elem._2.size
      num
    }.reduce(_ + _)

    logger.info(s"<><><> johnztitleNum count  ${johnztitleNum}")
    logger.info(s"<><><> johnztitleword count  ${johnztitlewordcount}")

    // geomatch over abstracts
    val johnzabstractMapCS = johnzrdd.map { art =>
      val geoList = if (geobc != null && geobc.value != null) {
        geobc.value.filter{
          georef =>
            art.textabs.
              contains(georef.name)
        }.toList
      } else {
        List[GeoName]()
      }
      (art.articleid, geoList)
    }.cache()

    johnzabstractMapCS.saveAsTextFile("/tmp/001_johnz_abstractmap")

    // abstract word count stats
    val johnzabstractwordcount = johnzrdd.map { art =>
      art.textabs.split(" ").size
    }.reduce(_ + _)

    // abstract geomatch number stats
    val johnzabstractNum = johnzabstractMapCS.map { elem =>
      val num = elem._2.size
      num
    }.reduce(_ + _)

    logger.info(s"<><><> johnzabstractNum count  ${johnzabstractNum}")
    logger.info(s"<><><> johnzabstractword count ${johnzabstractwordcount}")

    // geomatch over fulltext
    val johnzfullTextMapCS = johnzrdd.map { art =>
      val geoList = if (geobc != null && geobc.value != null) {
        geobc.value.filter{
          georef =>
            art.fulltext.
            contains(georef.name)
        }.toList
      } else {
        List[GeoName]()
      }
      (art.articleid, geoList)
    }.cache()

    johnzfullTextMapCS.saveAsTextFile("/tmp/001_johnz_fulltextmap")

    // fulltext word count stats
    val johnzfulltextwordcount = johnzrdd.map { art =>
      art.textabs.split(" ").size
    }.reduce(_ + _)

    // fulltext geomatch number stats
    val johnzfulltextNum = johnzfullTextMapCS.map { elem =>
      val num = elem._2.size
      num
    }.reduce(_ + _)

    logger.info(s"<><><> johnzfulltextNum count  ${johnzfulltextNum}")
    logger.info(s"<><><> johnzfulltextword count  ${johnzfulltextwordcount}")

    val johnztitleMean: Double = johnztitleNum.toDouble / johnzcount.toDouble
    val johnzabstractMean: Double = johnzabstractNum.toDouble / johnzcount.toDouble
    val johnzfullTextMean: Double = johnzfulltextNum.toDouble / johnzcount.toDouble

    val johnztitleWordcountMean: Double = johnztitlewordcount.toDouble / johnzcount.toDouble
    val johnzabstractWordcountMean: Double = johnzabstractwordcount.toDouble / johnzcount.toDouble
    val johnzfullTextWordcountMean: Double = johnzfulltextwordcount.toDouble / johnzcount.toDouble

    //*********************************************
    //
    //   MARINE
    //
    //*********************************************

    //*********************************************
    //
    //   GEOLOGY
    //
    //*********************************************


    // print single stats numbers at the very end again
    logger.info(s"<><><> all ${JOHNZ} articles ${count1} articles")
    logger.info(s"<><><> all ${JOHNZ} articles without stopwords ${johnzcount} filtered")

    logger.info(s"<><><> ${JOHNZ} title matches ${johnztitleNum} mean(title) : $johnztitleMean")
    logger.info(s"<><><> ${JOHNZ} abstract matches ${johnzabstractNum} mean(abstract) : $johnzabstractMean")
    logger.info(s"<><><> ${JOHNZ} fulltext matches ${johnzfulltextNum} mean(fulltext) : $johnzfullTextMean")

    logger.info(s"<><><> ${JOHNZ} title wordcount ${johnztitlewordcount} mean(title) : $johnztitleWordcountMean")
    logger.info(s"<><><> ${JOHNZ} abstract wordcount ${johnzabstractwordcount} mean(abstract) : $johnzabstractWordcountMean")
    logger.info(s"<><><> ${JOHNZ} fulltext wordcount ${johnzfulltextwordcount} mean(fulltext) : $johnzfullTextWordcountMean")

  }

}

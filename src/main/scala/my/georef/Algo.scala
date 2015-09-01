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

    // johnztitleMapCS.saveAsTextFile("/tmp/001_johnz_titlemap")

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

    // johnzabstractMapCS.saveAsTextFile("/tmp/001_johnz_abstractmap")

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

    // johnzfullTextMapCS.saveAsTextFile("/tmp/001_johnz_fulltextmap")

    // fulltext word count stats
    val johnzfulltextwordcount = johnzrdd.map { art =>
      art.fulltext.split(" ").size
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

    // REDUCE JOHNZ TITLE TO COLLECTION FOR CASSANDRA INSERT
    val johnzTitleCasCollection = johnztitleMapCS.map {
      case (articleid, geoList) =>
        val titlematch = geoList.map( geoname => geoname.name_id)
        (articleid, titlematch)
    }.saveToCassandra("geo", "geomatch", SomeColumns("articleid", "titlematch"))

    // REDUCE JOHNZ ABSTRACT TO COLLECTION FOR CASSANDRA INSERT
    val johnzAbstractCasCollection = johnzabstractMapCS.map {
      case (articleid, geoList) =>
        val abstractmatch = geoList.map( geoname => geoname.name_id)
        (articleid, abstractmatch)
    }.saveToCassandra("geo", "geomatch", SomeColumns("articleid", "abstractmatch"))

    // REDUCE JOHNZ FULLTEXT TO COLLECTION FOR CASSANDRA INSERT
    val johnzFulltextCasCollection = johnzfullTextMapCS.map {
      case (articleid, geoList) =>
        val fulltextmatch = geoList.map( geoname => geoname.name_id)
        (articleid, fulltextmatch)
    }.saveToCassandra("geo", "geomatch", SomeColumns("articleid", "fulltextmatch"))

    //*********************************************
    //
    //   MARINE
    //
    //*********************************************

    val marinefiltered1 = artrdd.select("articleid", "title", "textabs", "fulltext", "journal").where("journal = ?", MARINE).map { row =>
      Article(row.getLong("articleid"), row.getString("title"), row.getString("textabs"), row.getString("fulltext"), row.getString("journal"))
    }

    val marinecount1 = marinefiltered1.count()

    logger.info(s"<><><> start ${marinecount1} articles, planning $NUM_PARTITIONS partitions")

    // stop words and empty filtering
    val marinefiltered2 = DataLint.filterStopWords(marinefiltered1)
    val marinerdd = DataLint.filterEmptyFullText(marinefiltered2)
    // val forward = sc.parallelize(filtered3.collect()).cache()
    marinerdd.cache()
    val marinecount = marinerdd.count()

    logger.info(s"<><><> all $MARINE articles without stopwords ${marinecount} articles")

    // geomatch over titles
    val marinetitleMapCS = marinerdd.map { art =>
      val geoList = geobc.value.filter{
        georef =>
          art.title.
            contains(georef.name)
      }.toList
      (art.articleid, geoList)
    }.cache()

    // title word count stats
    val marinetitlewordcount = marinerdd.map { art =>
      art.title.split(" ").size
    }.reduce(_ + _)

    // marinetitleMapCS.saveAsTextFile("/tmp/001_marine_titlemap")

    // title geomatch number stats
    val marinetitleNum = marinetitleMapCS.map { elem =>
      val num = elem._2.size
      num
    }.reduce(_ + _)

    logger.info(s"<><><> marinetitleNum count  ${marinetitleNum}")
    logger.info(s"<><><> marinetitleword count  ${marinetitlewordcount}")

    // geomatch over abstracts
    val marineabstractMapCS = marinerdd.map { art =>
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

    // marineabstractMapCS.saveAsTextFile("/tmp/001_marine_abstractmap")

    // abstract word count stats
    val marineabstractwordcount = marinerdd.map { art =>
      art.textabs.split(" ").size
    }.reduce(_ + _)

    // abstract geomatch number stats
    val marineabstractNum = marineabstractMapCS.map { elem =>
      val num = elem._2.size
      num
    }.reduce(_ + _)

    logger.info(s"<><><> marineabstractNum count  ${marineabstractNum}")
    logger.info(s"<><><> marineabstractword count ${marineabstractwordcount}")

    // geomatch over fulltext
    val marinefullTextMapCS = marinerdd.map { art =>
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

    // marinefullTextMapCS.saveAsTextFile("/tmp/001_marine_fulltextmap")

    // fulltext word count stats
    val marinefulltextwordcount = marinerdd.map { art =>
      art.fulltext.split(" ").size
    }.reduce(_ + _)

    // fulltext geomatch number stats
    val marinefulltextNum = marinefullTextMapCS.map { elem =>
      val num = elem._2.size
      num
    }.reduce(_ + _)

    logger.info(s"<><><> marinefulltextNum count  ${marinefulltextNum}")
    logger.info(s"<><><> marinefulltextword count  ${marinefulltextwordcount}")

    val marinetitleMean: Double = marinetitleNum.toDouble / marinecount.toDouble
    val marineabstractMean: Double = marineabstractNum.toDouble / marinecount.toDouble
    val marinefullTextMean: Double = marinefulltextNum.toDouble / marinecount.toDouble

    val marinetitleWordcountMean: Double = marinetitlewordcount.toDouble / marinecount.toDouble
    val marineabstractWordcountMean: Double = marineabstractwordcount.toDouble / marinecount.toDouble
    val marinefullTextWordcountMean: Double = marinefulltextwordcount.toDouble / marinecount.toDouble

    // REDUCE JOHNZ TITLE TO COLLECTION FOR CASSANDRA INSERT
    val marineTitleCasCollection = marinetitleMapCS.map {
      case (articleid, geoList) =>
        val titlematch = geoList.map( geoname => geoname.name_id)
        (articleid, titlematch)
    }.saveToCassandra("geo", "geomatch", SomeColumns("articleid", "titlematch"))

    // REDUCE JOHNZ ABSTRACT TO COLLECTION FOR CASSANDRA INSERT
    val marineAbstractCasCollection = marineabstractMapCS.map {
      case (articleid, geoList) =>
        val abstractmatch = geoList.map( geoname => geoname.name_id)
        (articleid, abstractmatch)
    }.saveToCassandra("geo", "geomatch", SomeColumns("articleid", "abstractmatch"))

    // REDUCE JOHNZ FULLTEXT TO COLLECTION FOR CASSANDRA INSERT
    val marineFulltextCasCollection = marinefullTextMapCS.map {
      case (articleid, geoList) =>
        val fulltextmatch = geoList.map( geoname => geoname.name_id)
        (articleid, fulltextmatch)
    }.saveToCassandra("geo", "geomatch", SomeColumns("articleid", "fulltextmatch"))

    //*********************************************
    //
    //   GEOLOGY
    //
    //*********************************************

    val geologyfiltered1 = artrdd.select("articleid", "title", "textabs", "fulltext", "journal").where("journal = ?", GEOLOGY).map { row =>
      Article(row.getLong("articleid"), row.getString("title"), row.getString("textabs"), row.getString("fulltext"), row.getString("journal"))
    }

    val geologycount1 = geologyfiltered1.count()

    logger.info(s"<><><> start ${geologycount1} articles, planning $NUM_PARTITIONS partitions")

    // stop words and empty filtering
    val geologyfiltered2 = DataLint.filterStopWords(geologyfiltered1)
    val geologyrdd = DataLint.filterEmptyFullText(geologyfiltered2)
    // val forward = sc.parallelize(filtered3.collect()).cache()
    geologyrdd.cache()
    val geologycount = geologyrdd.count()

    logger.info(s"<><><> all $MARINE articles without stopwords ${geologycount} articles")

    // geomatch over titles
    val geologytitleMapCS = geologyrdd.map { art =>
      val geoList = geobc.value.filter{
        georef =>
          art.title.
            contains(georef.name)
      }.toList
      (art.articleid, geoList)
    }.cache()

    // title word count stats
    val geologytitlewordcount = geologyrdd.map { art =>
      art.title.split(" ").size
    }.reduce(_ + _)

    // geologytitleMapCS.saveAsTextFile("/tmp/001_geology_titlemap")

    // title geomatch number stats
    val geologytitleNum = geologytitleMapCS.map { elem =>
      val num = elem._2.size
      num
    }.reduce(_ + _)

    logger.info(s"<><><> geologytitleNum count  ${geologytitleNum}")
    logger.info(s"<><><> geologytitleword count  ${geologytitlewordcount}")

    // geomatch over abstracts
    val geologyabstractMapCS = geologyrdd.map { art =>
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

    // geologyabstractMapCS.saveAsTextFile("/tmp/001_geology_abstractmap")

    // abstract word count stats
    val geologyabstractwordcount = geologyrdd.map { art =>
      art.textabs.split(" ").size
    }.reduce(_ + _)

    // abstract geomatch number stats
    val geologyabstractNum = geologyabstractMapCS.map { elem =>
      val num = elem._2.size
      num
    }.reduce(_ + _)

    logger.info(s"<><><> geologyabstractNum count  ${geologyabstractNum}")
    logger.info(s"<><><> geologyabstractword count ${geologyabstractwordcount}")

    // geomatch over fulltext
    val geologyfullTextMapCS = geologyrdd.map { art =>
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

    // geologyfullTextMapCS.saveAsTextFile("/tmp/001_geology_fulltextmap")

    // fulltext word count stats
    val geologyfulltextwordcount = geologyrdd.map { art =>
      art.fulltext.split(" ").size
    }.reduce(_ + _)

    // fulltext geomatch number stats
    val geologyfulltextNum = geologyfullTextMapCS.map { elem =>
      val num = elem._2.size
      num
    }.reduce(_ + _)

    logger.info(s"<><><> geologyfulltextNum count  ${geologyfulltextNum}")
    logger.info(s"<><><> geologyfulltextword count  ${geologyfulltextwordcount}")

    val geologytitleMean: Double = geologytitleNum.toDouble / geologycount.toDouble
    val geologyabstractMean: Double = geologyabstractNum.toDouble / geologycount.toDouble
    val geologyfullTextMean: Double = geologyfulltextNum.toDouble / geologycount.toDouble

    val geologytitleWordcountMean: Double = geologytitlewordcount.toDouble / geologycount.toDouble
    val geologyabstractWordcountMean: Double = geologyabstractwordcount.toDouble / geologycount.toDouble
    val geologyfullTextWordcountMean: Double = geologyfulltextwordcount.toDouble / geologycount.toDouble

    // REDUCE JOHNZ TITLE TO COLLECTION FOR CASSANDRA INSERT
    val geologyTitleCasCollection = geologytitleMapCS.map {
      case (articleid, geoList) =>
        val titlematch = geoList.map( geoname => geoname.name_id)
        (articleid, titlematch)
    }.saveToCassandra("geo", "geomatch", SomeColumns("articleid", "titlematch"))

    // REDUCE JOHNZ ABSTRACT TO COLLECTION FOR CASSANDRA INSERT
    val geologyAbstractCasCollection = geologyabstractMapCS.map {
      case (articleid, geoList) =>
        val abstractmatch = geoList.map( geoname => geoname.name_id)
        (articleid, abstractmatch)
    }.saveToCassandra("geo", "geomatch", SomeColumns("articleid", "abstractmatch"))

    // REDUCE JOHNZ FULLTEXT TO COLLECTION FOR CASSANDRA INSERT
    val geologyFulltextCasCollection = geologyfullTextMapCS.map {
      case (articleid, geoList) =>
        val fulltextmatch = geoList.map( geoname => geoname.name_id)
        (articleid, fulltextmatch)
    }.saveToCassandra("geo", "geomatch", SomeColumns("articleid", "fulltextmatch"))

    // print single stats numbers at the very end again
    logger.info(s"<><><> all ${JOHNZ} articles ${count1} articles")
    logger.info(s"<><><> all ${JOHNZ} articles without stopwords ${johnzcount} filtered")

    logger.info(s"<><><> ${JOHNZ} title matches ${johnztitleNum} mean(title) : $johnztitleMean")
    logger.info(s"<><><> ${JOHNZ} abstract matches ${johnzabstractNum} mean(abstract) : $johnzabstractMean")
    logger.info(s"<><><> ${JOHNZ} fulltext matches ${johnzfulltextNum} mean(fulltext) : $johnzfullTextMean")

    logger.info(s"<><><> ${JOHNZ} title wordcount ${johnztitlewordcount} mean(title) : $johnztitleWordcountMean")
    logger.info(s"<><><> ${JOHNZ} abstract wordcount ${johnzabstractwordcount} mean(abstract) : $johnzabstractWordcountMean")
    logger.info(s"<><><> ${JOHNZ} fulltext wordcount ${johnzfulltextwordcount} mean(fulltext) : $johnzfullTextWordcountMean")

    // print single stats numbers at the very end again
    logger.info(s"<><><> all ${MARINE} articles ${marinecount1} articles")
    logger.info(s"<><><> all ${MARINE} articles without stopwords ${marinecount} filtered")

    logger.info(s"<><><> ${MARINE} title matches ${marinetitleNum} mean(title) : $marinetitleMean")
    logger.info(s"<><><> ${MARINE} abstract matches ${marineabstractNum} mean(abstract) : $marineabstractMean")
    logger.info(s"<><><> ${MARINE} fulltext matches ${marinefulltextNum} mean(fulltext) : $marinefullTextMean")

    logger.info(s"<><><> ${MARINE} title wordcount ${marinetitlewordcount} mean(title) : $marinetitleWordcountMean")
    logger.info(s"<><><> ${MARINE} abstract wordcount ${marineabstractwordcount} mean(abstract) : $marineabstractWordcountMean")
    logger.info(s"<><><> ${MARINE} fulltext wordcount ${marinefulltextwordcount} mean(fulltext) : $marinefullTextWordcountMean")

    // print single stats numbers at the very end again
    logger.info(s"<><><> all ${GEOLOGY} articles ${geologycount1} articles")
    logger.info(s"<><><> all ${GEOLOGY} articles without stopwords ${geologycount} filtered")

    logger.info(s"<><><> ${GEOLOGY} title matches ${geologytitleNum} mean(title) : $geologytitleMean")
    logger.info(s"<><><> ${GEOLOGY} abstract matches ${geologyabstractNum} mean(abstract) : $geologyabstractMean")
    logger.info(s"<><><> ${GEOLOGY} fulltext matches ${geologyfulltextNum} mean(fulltext) : $geologyfullTextMean")

    logger.info(s"<><><> ${GEOLOGY} title wordcount ${geologytitlewordcount} mean(title) : $geologytitleWordcountMean")
    logger.info(s"<><><> ${GEOLOGY} abstract wordcount ${geologyabstractwordcount} mean(abstract) : $geologyabstractWordcountMean")
    logger.info(s"<><><> ${GEOLOGY} fulltext wordcount ${geologyfulltextwordcount} mean(fulltext) : $geologyfullTextWordcountMean")

    val johnzStats = JournalStats(JOHNZ, count1, johnzcount, johnztitleNum, johnzabstractNum, johnzfulltextNum,
      johnztitlewordcount, johnzabstractwordcount, johnzfulltextwordcount,
      johnztitleMean, johnzabstractMean, johnzfullTextMean, johnztitleWordcountMean, johnzabstractWordcountMean, johnzfullTextWordcountMean
    )
    val marineStats = JournalStats(MARINE, marinecount1, marinecount, marinetitleNum, marineabstractNum, marinefulltextNum,
      marinetitlewordcount, marineabstractwordcount, marinefulltextwordcount,
      marinetitleMean, marineabstractMean, marinefullTextMean, marinetitleWordcountMean, marineabstractWordcountMean, marinefullTextWordcountMean
    )
    val geologyStats = JournalStats(GEOLOGY, geologycount1, geologycount, geologytitleNum, geologyabstractNum, geologyfulltextNum,
      geologytitlewordcount, geologyabstractwordcount, geologyfulltextwordcount,
      geologytitleMean, geologyabstractMean, geologyfullTextMean, geologytitleWordcountMean, geologyabstractWordcountMean, geologyfullTextWordcountMean
    )
    val collection = sc.parallelize(Seq(johnzStats, marineStats, geologyStats))

    collection.saveAsCassandraTable("geo", "journalstats", SomeColumns("journal", "numArticles", "numArticlesFiltered", "numTitleMatch",
    "numAbstractMatch", "numFullTextMatch", "numTitleWords", "numAbstractWords", "numFullTextWords", "meanTitleMatch",
    "meanAbstractMatch", "meanFullTextMatch", "meanTitleWords", "meanAbstractWords", "meanFullTextWords"))

  }


}

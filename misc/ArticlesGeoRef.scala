import com.datastax.spark.connector._
import my.georef.{Article, GeoName}
import org.apache.spark._
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.broadcast._

object GeoTest extends Serializable with Logging {

  def accessBC(bcgeod: Broadcast[Array[GeoName]]): Int = {
    bcgeod.value.length
  }

  def createBC(sc: SparkContext) : Broadcast[Array[GeoName]] = {

    val geordd = sc.cassandraTable("geo", "linzgeo")
    val georrdcase = geordd.select("name_id", "name").map { row =>
      GeoName(row.getLong("name_id"), row.getString("name"))
    }
    val geofiltered = serialObs.filterEmptyGeoNAmes(georrdcase)

    val gnc = geofiltered.collect()
    val geobcd = sc.broadcast(gnc)
    geobcd
  }

}

object ArticlesGeoRef extends App with Logging with Serializable {

  val conf = new SparkConf(true).set("spark.cassandra.connection.host", "127.0.0.1")
  val sc = new SparkContext("spark://127.0.0.1:7077", "test", conf)

  val _JOHNZ = "New Zealand Journal of Hydrology"
  val _MARINE = "New Zealand Journal of Marine and Freshwater Research"
  val _GEOLOGY = "New Zealand Journal of Geology and Geophysics"

  val geobc = GeoTest.createBC(sc)

  println(s"geobc ${geobc.value.size} GeoTest ${GeoTest.accessBC(geobc)} ")
  logInfo(s"geobc ${geobc.value.size} GeoTest ${GeoTest.accessBC(geobc)} ")



  val artrdd = sc.cassandraTable("geo", "articles")

  val filtered1 = artrdd.select("articleid", "title", "textabs", "fulltext", "journal").where("journal = ?", _JOHNZ).map { row =>
    Article(row.getLong("articleid"), row.getString("title"), row.getString("textabs"), row.getString("fulltext"), row.getString("journal"))
  }

  val count = filtered1.count()
  val NUM_PARTITIONS = 2

  println(s"start ${count} articles, planning $NUM_PARTITIONS partitions")
  logInfo(s"start ${count} articles, planning $NUM_PARTITIONS partitions")

  val filtered2 = serialObs.filterStopWords(filtered1)
  val forward = serialObs.filterEmptyFullText(filtered2)
  // val forward = sc.parallelize(filtered3.collect()).cache()
  forward.cache()
  val count2 = forward.count()

  println(s"all articles without stopwords ${count2} articles")
  logInfo(s"all articles without stopwords ${count2} articles")

  // stats per journal JOHNZ
  val johnztitleMapCS = forward.map { art =>
    val differential = geobc.value.size
    val geoList = geobc.value.toSeq.filter{
        georef =>
          art.title.
            contains(georef.name)
      }.toList

    (art.articleid, geoList)

  }.cache()

  // title stats
  val tjtc = johnztitleMapCS.count()

  johnztitleMapCS.saveAsTextFile("/tmp/001_titlemap")

  val johnztitleNum = johnztitleMapCS.map { elem =>
      val num = elem._2.size
      num
    }.reduce(_ + _)

  println(s"johnztitleMapCS count  ${tjtc}")

  // case sensitive
  val johnzabstractMapCS = forward.map { art =>
    val geoList = if (geobc != null && geobc.value != null) {
      geobc.value.toSeq.filter{
        georef =>
          art.textabs.
            contains(georef.name)
      }.toList
    } else {
      List[GeoName]()
    }
    // val geoList = matchGeos(art.textabs)
    logInfo(s"${art.articleid} match list ${geoList.size}")
    (art.articleid, geoList)
  }.cache()

  // case sensitive
  val johnzfullTextMapCS = forward.map { art =>
    val geoList = if (geobc != null && geobc.value != null) {
      geobc.value.toSeq.filter{
        georef =>
          art.fulltext.
            contains(georef.name)
      }.toList
    } else {

      List[GeoName]()
    }
    // val geoList = matchGeos(art.fulltext)
    logInfo(s"${art.articleid} match list ${geoList.size}")
    (art.articleid, geoList)
  }.cache()

  val tjac = johnzabstractMapCS.count()
  val johnzabstractNum = johnzabstractMapCS.map { elem =>
      val num = elem._2.size
      num
    }.reduce(_ + _)

  println(s"johnzabstractMapCS count  ${tjac}")

  val tjfc = johnzfullTextMapCS.count()
  val johnzfulltextNum = johnzfullTextMapCS.map { elem =>
      val num = elem._2.size
      num
    }.reduce(_ + _)

  println(s"johnzfullTextMapCS count  ${tjfc}")

  val johnztitleMean = johnztitleNum / count2
  val johnzabstractMean = johnzabstractNum / count2
  val johnzfullTextMean = johnzfulltextNum / count2

  // print single stats numbers at the very end again
  println(s"all articles ${count} articles")
  println(s"all articles without stopwords ${count2} articles")
  println(s"all articles ${_JOHNZ} $count2 all articles")
  println(s"${_JOHNZ} title matches ${johnztitleNum} mean(title) : $johnztitleMean")
  println(s"${_JOHNZ} abstract matches ${johnzabstractNum} mean(abstract) : $johnzabstractMean")
  println(s"${_JOHNZ} fulltext matches ${johnzfulltextNum} mean(fulltext) : $johnzfullTextMean")

  logInfo(s"all articles ${count} articles")
  logInfo(s"all articles without stopwords ${count2} articles")
  logInfo(s"all articles ${_JOHNZ} $count2 all articles")
  logInfo(s"${_JOHNZ} title matches ${johnztitleNum} mean(title) : $johnztitleMean")
  logInfo(s"${_JOHNZ} abstract matches ${johnzabstractNum} mean(abstract) : $johnzabstractMean")
  logInfo(s"${_JOHNZ} fulltext matches ${johnzfulltextNum} mean(fulltext) : $johnzfullTextMean")
}

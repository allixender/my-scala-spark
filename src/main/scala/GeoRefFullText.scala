import org.apache.spark._
import com.datastax.spark.connector._
import org.apache.spark.rdd.RDD
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.broadcast._

object serialGeo2 extends Serializable {

  def findGeoRefsInText(testString: String, bcgeo: Broadcast[Array[GeoName]]): List[GeoName] = {
    val filtered = bcgeo.value.filter{ georef =>
      testString.toLowerCase.contains(georef.name.toLowerCase)
    }.toList
    filtered
  }

  def findGeoRefsInTextCaseSensitive(testString: String, bcgeo: Broadcast[Array[GeoName]]): List[GeoName] = {
    val filtered = bcgeo.value.filter{ georef =>
      testString.contains(georef.name)
    }.toList
    filtered
  }

  def findGeoRefsInTextRegex(testString: String, bcgeo: Broadcast[Array[GeoName]]): List[GeoName] = {
    val filtered = bcgeo.value.filter{ georef =>
      testString.toLowerCase().contains(georef.name.toLowerCase())
    }.toList
    filtered
  }

}

object GeoRefFullText {

  def main(args: Array[String]) {

    val conf = new SparkConf(true).set("spark.cassandra.connection.host", "127.0.0.1")
    val sc = new SparkContext("spark://127.0.0.1:7077", "test", conf)

    val geordd = sc.cassandraTable("geo", "linzgeo")
    val georrdcase = geordd.select("name_id", "name").map { row =>
      GeoName(row.getLong("name_id"), row.getString("name"))
    }
    georrdcase.cache()
    val geobc = sc.broadcast(georrdcase.collect())

    val artrdd = sc.cassandraTable("geo", "articles")

    val filtered1 = artrdd.select("articleid","title","textabs").map { row =>
      Article(row.getLong("articleid"), row.getString("title"), row.getString("textabs"), row.getString("fulltext"))
    }

    val count = filtered1.count()
    println(s"start ${count} articles")

    val filtered2 = serialObs.filterStopWords(filtered1)
    val filtered3 = serialObs.filterEmptyFullText(filtered2)
    val forward = sc.parallelize(filtered3.collect()).cache()
    val count2 = forward.count()

    println(s"without stopwords ${count2} articles")

    val fullTextMap = forward.repartition(4).mapPartitions { iter =>
      iter.map{ art =>
        val geoList = serialGeo2.findGeoRefsInText(art.fulltext, geobc)
        (art.articleid, geoList)
      }
    }.cache()

    fullTextMap.saveAsTextFile("/home/akmoch/dev/build/lucene-hydroabstracts-scala/misc/royal-files/spark/fulltext")

    val fulltextNum1 = fullTextMap.map{ elem =>
      val num = elem._2.size
      num
    }.reduce(_+_)

    // case sensitive
    val fullTextMapCS = forward.repartition(4).mapPartitions { iter =>
      iter.map{ art =>
        val geoList = serialGeo2.findGeoRefsInTextCaseSensitive(art.fulltext, geobc)
        (art.articleid, geoList)
      }
    }.cache()

    fullTextMapCS.saveAsTextFile("/home/akmoch/dev/build/lucene-hydroabstracts-scala/misc/royal-files/spark/fulltext_cs")

    val fulltextNum2 = fullTextMapCS.map{ elem =>
      val num = elem._2.size
      num
    }.reduce(_+_)

    val mean1 = fulltextNum1 / count2
    val mean2 = fulltextNum2 / count2
    println(s"geocollected title matches ${fulltextNum1} mean(title) : $mean1")
    println(s"geocollected title matches ${fulltextNum2} mean(title) : $mean2 Case Sensitive")
  }
}

package my.georef

import org.apache.spark.SparkContext
import com.datastax.spark.connector._
import org.apache.spark.SparkConf
import com.datastax.spark.connector._
import com.typesafe.scalalogging.LazyLogging
import my.georef.datapreps.{Article, DataLint, GeoName}
import my.georef.ogcxml.MetaData

import scala.util.matching.Regex
import org.apache.spark.sql.{Encoder, SQLContext, SparkSession}
import org.apache.spark.ml.feature.{HashingTF, RegexTokenizer, StopWordsRemover, Tokenizer}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.SparkSession
import org.apache.spark.ml.feature.{HashingTF, IDF, Tokenizer}

object KeywordsCompute extends LazyLogging {

  val JOHNZ = "New Zealand Journal of Hydrology"
  val MARINE = "New Zealand Journal of Marine and Freshwater Research"
  val GEOLOGY = "New Zealand Journal of Geology and Geophysics"

  def mainAlgo(args: Array[String]): Unit = {

    val conf = new SparkConf(true).set("spark.cassandra.connection.host", "127.0.0.1")
    implicit val sc = new SparkContext("spark://127.0.0.1:7077", "test", conf)

    implicit val spark = SparkSession.builder.appName("idftest").config(conf).master("local").getOrCreate()

    val artrdd = sc.cassandraTable[Article]("geo", "articles")

    // For implicit conversions from RDDs to DataFrames
    import spark.implicits._
    val casDF = artrdd.toDF()

    // implicit val mapEncoder = org.apache.spark.sql.Encoders.kryo[Map[String, Any]]

    val titletoken = new Tokenizer().setInputCol("title").setOutputCol("titlewords")
    val titlecountTokens = udf { (titlewords: Seq[String]) => titlewords.length }

    val titletokenized = titletoken.transform(casDF)
    // titletokenized.select("title", "titlewords").withColumn("titletokens", titlecountTokens(col("titlewords"))).show(false)

    val remover = new StopWordsRemover().setInputCol("titlewords").setOutputCol("titlefiltered")
    //remover.transform(titletokenized).show(false)
    val removeData = remover.transform(titletokenized)

    val hashingTF = new HashingTF().setInputCol("titlefiltered").setOutputCol("rawFeatures").setNumFeatures(10000)
    val featurizedData = hashingTF.transform(removeData)

    val idf = new IDF().setInputCol("rawFeatures").setOutputCol("features")
    val idfModel = idf.fit(featurizedData)
    val rescaledData = idfModel.transform(featurizedData)

    //rescaledData.select("titlefiltered", "features").show(10, false)
    // rescaledData.createCassandraTable(
    //  "geo",
    //  "keywords",
    //  partitionKeyColumns = Some(Seq("articleid")),
    //  clusteringKeyColumns = Some(Seq("keywordlist")))

    rescaledData.write
      .format("org.apache.spark.sql.cassandra")
      .options(Map( "table" -> "keywords", "keyspace" -> "geo"))
      .save()
  }

  /**
    * should join title and abstract into one feature, regex tokenize and remove stop words
    *
    * label would be articleid
    *
    * should know how many distinct words are in each of the 3 journals jointly over their title and abstracts
    *
    * should then build the "vocabulary" of title and abstract" for each journal separately, by knowing the overall distinct words count
    *
    * then we can select topmost 5-6 keywords jointly per article
    *
   */



}












package my.georef.datapreps

import com.typesafe.scalalogging.LazyLogging
import my.georef.ogcxml.MetaData
import org.apache.spark.rdd.RDD
import com.vividsolutions.jts.geom._
import org.geotools.geometry.DirectPosition2D
import org.geotools.referencing.CRS
import org.opengis.geometry.MismatchedDimensionException
import org.opengis.referencing.{FactoryException, NoSuchAuthorityCodeException}
import org.opengis.referencing.crs.CoordinateReferenceSystem
import org.opengis.referencing.operation.{MathTransform, TransformException}

import org.apache.spark.ml.feature.StopWordsRemover
import org.apache.spark.ml.feature.{RegexTokenizer, Tokenizer}
import org.apache.spark.sql.functions._


case class GeoName(
                    name_id: Long,
                    name: String,
                    crd_datum: String,
                    crd_east: Double,
                    crd_latitude: Double,
                    crd_longitude: Double,
                    crd_north: Double,
                    crd_projection: String,
                    land_district: String,
                    status: String ) extends Serializable

case class Article(articleid: Long,
                   journal: String,
                   arturl: String,
                   author: String,
                   authortitle: String,
                   fulltext: String,
                   textabs: String,
                   title: String,
                   year: Long ) extends Serializable

case class JournalStats(
                         journal: String,  //
                         numArticles: Long,  // count1
                         numArticlesFiltered: Long, // count
                         numTitleMatch: Long,
                         numAbstractMatch: Long,
                         numFullTextMatch: Long,
                         numTitleWords: Long,
                         numAbstractWords: Long,
                         numFullTextWords: Long,
                         meanTitleMatch: Double,
                         meanAbstractMatch: Double,
                         meanFullTextMatch: Double,
                         meanTitleWords: Double,
                         meanAbstractWords: Double,
                         meanFullTextWords: Double )

object DataLint extends Serializable with LazyLogging {

  val noAbstractWords = List("No abstract", "No Abstract", "No abstract available.").map(x => x.toLowerCase())

  val noTitleWords = List("Book Review", "Book Reviews", "Book reviews", "Editorial", "Foreword", "News", "Presidential Address", "Forthcoming Events",
    "Notices", "Notes", "Abstracts", "Reports", "Letters To The Editor", "IHD Bulletin", "Errata", "Notice", "Reviews", "Forthcoming events",
    "Obituary", "List of Reviewers", "Summary Contents", "News, Future meetings, Corrigenda for Vol 19",
    "Discussion - New Zealand Hydrological Society symposium on climate change, snow and ice, and lakes",
    "Corrigendum to Pearce, A.J., Rowe, L.K. 1984. Hydrology Of mid-altitude tussock grasslands, Upper Waipori Catchment: II - Water balance, flow duration, and storm runoff. 23(2):607-72",
    "Metrication in scientific publications", "Invited Editorial",
    "Abstracts of Hydrology Symposium: Soil conservators refresher course, Blenheim, 21 March 1963",
    "Reviews Of Theme 1 - Methods for assessing slope erosion and non-channel sediment sources in upland regions",
    "Reviews of Theme 3 - Human impact on erosion and sediment yield in steeplands",
    "New Zealand Hydrological Society List of Members At 31 May, 1963",
    "Reviews of Theme 2 - Stream channel dynamics and morphology",
    "Editorial: Units, coefficients and dimensions",
    "Editorial Comment", "Reviews On Theme 4 - Impacts and management of steeplands erosion",
    "Errata Subglacial sediment accumulation and basal smoothing -- a mechanism for initiating glacier surging",
    "Report On International Association of Hydrological Sciences Assembly",
    "Journal of Hydrology (N.Z.)Author Index Volumes 1 - 21",
    "List of Recent University Theses Reporting Hydrological Studies",
    "Forthcoming Events - Corrigenda Vol. 18.",
    "Obituary: Mr E.J. Speight", "Future Meetings",
    "Presidential Address 1963", "News And Book Reviews", "Forthcoming Meetings", "Appreciation",
    "Changes in emphases for the Hydrological Society",
    "Journal of Hydrology (NZ) 5 Year Index Volumes 31 - 35",
    "Journal of Hydrology (NZ) 5 Year Index Volumes 36 - 40",
    "Abstracts - Finkelstein, J.",
    "P. Report - International Hydrology Programme,WMO Hydrology and Water Resources Programme, and International Association of Hydrological Sciences",
    "Current research in hydrology in New Zealand universities",
    "Report 26th Congress of The International Geographical Union, August 21-26 1988 Sydney, Australia",
    "1981 Symposium Delegates",
    "Hydrology and the environment (Opening address to the N.Z. Hydrological Society Annual Symposium, University Of Auckland, November 1975)",
    "News - Worldwide survey collects information on artifical recharge of ground water",
    "N Cherry. Editorial: Responsible Science",
    "The Royal Society",
    "Hydrological impressions from a visit to the U.S.",
    "The slackline cableway used in England",
    "Presidential address",
    "A note from the editor",
    "Travel report. US Experience With Transferable Water Permits",
    "Abstracts - Grant, P.J.",
    "Abstracts - Campbell, A.P",
    "Letters to the Editor",
    "Report International Symposium On Erosion And Sedimentation In The Pacific Rim 3-7 August 1987, Corvaillis, Oregon, U.S.A",
    "Errata for Woods & Rowe, p51-86",
    "Report Symposium On Large Scale Effects Of Seasonal Snow Cover IAHS /IUGG Vancouver, August 1987",
    "Editorial: Hydrology and the administrator",
    "Book Review, Publications received, Forthcoming meetings",
    "New Publications and Forthcoming Meetings And Courses",
    "IHD Bulletin New Zealand",
    "Report Australian Study Tour And Groundwater Conference",
    "Report 1986 Hydrology And Water Resources Symposium",
    "Letter",
    "Notice - Travel Grants - Science Awards",
    "Journal of Hydrology (N.Z.) Ten Year Index",
    "In Memorium").map(x => x.toLowerCase)

  def testNoTitleReverse(testW: String): Boolean = {
    val noTitleWords_ = this.noTitleWords
    val trueMatchesFound = noTitleWords_.map { stopW =>
      val res1 = if (testW.toLowerCase().contains(stopW.toLowerCase())) {
        true
      } else {
        false
      }
      res1
    }.count(testB => testB && true)

    (trueMatchesFound > 0)
  }

  def testNoAbstractReverse(testW: String): Boolean = {
    val noAbstractWords_ = this.noAbstractWords
    val trueMatchesFound = noAbstractWords_.map { stopW =>
      val res1 = if (testW.toLowerCase().contains(stopW.toLowerCase())) {
        true
      } else {
        false
      }
      res1
    }.count(testB => testB && true)

    (trueMatchesFound > 0)
  }

  def filterStopWords(rdd: RDD[Article]): RDD[Article] = {
    rdd.filter { article =>
      !testNoTitleReverse(article.title)
    }.filter { article =>
      !testNoAbstractReverse(article.textabs)
    }
  }

  def filterEmptyFullText(rdd: RDD[Article]): RDD[Article] = {
    rdd.filter { article =>
      article.fulltext != null && !article.fulltext.isEmpty
    }.filter { article =>
      article.textabs != null && !article.textabs.isEmpty
    }.filter { article =>
      article.title != null && !article.title.isEmpty
    }
  }

  def enrichGeoRef(geoMatches: List[GeoName], tmpMeta: MetaData) : MetaData = {

    val finalBbox = if (!geoMatches.isEmpty) {
      // val thing = hintOfficalFull.distinct.map(refElem => s"${refElem.name_id}: ${refElem.name}")
      val bbox = getBboxFromRefs(geoMatches)
      // logger.debug(s"OFFICIAL GEOREF ### $thing} ### $bbox")
      tmpMeta.gmd_identificationInfo_MD_DataIdentification_extent_Description =
        geoMatches.map(refElem => refElem.name).distinct.mkString(", ")
      bbox
    }
    else {

      val lowerLeft = GeoName(123456,
        "lowerLeft",
        "NZGD2000",
        0.0,
        -47.93848,
        165.39060,
        0.0,
        "",
        "",
        "Official")

      val upperRight = GeoName(123457,
        "upperRight",
        "NZGD2000",
        0.0,
        -34.02613,
        178.73954,
        0.0,
        "",
        "",
        "Official")

      val bbox = getBboxFromRefs(List(lowerLeft, upperRight))
      // logger.debug(s"NEW ZEALAND GEOREF ### $bbox")
      tmpMeta.gmd_identificationInfo_MD_DataIdentification_extent_Description = "generic article, or no geo-reference found"
      bbox
    }

    tmpMeta.mapExtentCoordinates = s"BBOX ( ${finalBbox.getMinX()} W ${finalBbox.getMinY()} S  ${finalBbox.getMaxX()} E ${finalBbox.getMaxY()} N )"
    tmpMeta.gmd_identificationInfo_MD_DataIdentification_extent_BBOX_westBoundLongitude = finalBbox.getMinX()
    tmpMeta.gmd_identificationInfo_MD_DataIdentification_extent_BBOX_eastBoundLongitude = finalBbox.getMaxX()
    tmpMeta.gmd_identificationInfo_MD_DataIdentification_extent_BBOX_southBoundLatitude = finalBbox.getMinY()
    tmpMeta.gmd_identificationInfo_MD_DataIdentification_extent_BBOX_northBoundLatitude = finalBbox.getMaxY()

    tmpMeta
  }

  def getBboxFromRefs(refs:List[GeoName]) : Envelope = {


    val bboxer = new StringBuilder

    // var points: Array[Point] = new Array(refs.size)
    val gf: GeometryFactory = new GeometryFactory()

    val pointList = for {

      ref <- refs

      fullPoint = if (ref.crd_datum != null && !ref.crd_datum.isEmpty()) {
        // logger.debug(s"${ref.crd_datum} ${ref.crd_longitude} ${ref.crd_latitude} ")
        val srs = getEPSG4Name(ref.crd_datum)
        val coords = recodePointTo4326((ref.crd_longitude.toDouble, ref.crd_latitude.toDouble), srs)

        val newPoint: Point = gf.createPoint(new Coordinate(coords._1, coords._2))

        // bboxer.append(s"${srs} ${coords._1} ${coords._2} ")
        newPoint
      }
      else if (ref.crd_projection != null && !ref.crd_projection.isEmpty()) {
        // logger.debug(s"${ref.crd_projection} ${ref.crd_east} ${ref.crd_north} ")
        val srs = getEPSG4Name(ref.crd_datum)
        val coords = recodePointTo4326((ref.crd_east.toDouble, ref.crd_north.toDouble), srs)
        val newPoint: Point = gf.createPoint(new Coordinate(coords._1, coords._2))

        // bboxer.append(s"${srs} ${coords._1} ${coords._2} ")
        newPoint
      }
      else {
        gf.createPoint(new Coordinate(0.0, 0.0))
      }
    } yield fullPoint

    val points = pointList.filter(poi => ( !(poi.getCoordinate().x == 0.0) && !(poi.getCoordinate().y == 0.0)) )



    var geom: MultiPoint = new MultiPoint(points.toArray, gf)
    geom.setSRID(4326)
    val envelope = geom.getEnvelopeInternal()

    // bboxer.toString()
    // envelope.toString()
    envelope
  }

  def getEPSG4Name(srs_name: String) : String = {
    srs_name match {
      case "NZGD2000" => "EPSG:4326" // 4167
      case "RSRGD2000" => "EPSG:4765"
      case "NZTM" => "EPSG:2193"
      case "NZGD49" => "EPSG:4272"
      case _ => "EPSG:4326"
    }
  }

  // lat, lon input
  def recodePointTo4326( coords:(Double, Double), source_srs: String ) :  (Double, Double) = {

    val coordsswitchlist = List("EPSG:27200", "EPSG:900913", "EPSG:3785")

    var coordswitch = false;
    val lenient = true;

    val miny = coords._1
    val minx = coords._2

    val retTuple = try {
      val sourceCrs: CoordinateReferenceSystem = CRS.decode(source_srs)
      val targetCrs: CoordinateReferenceSystem = CRS.decode("EPSG:4326")

      val mathTransform: MathTransform = CRS.findMathTransform(sourceCrs,
        targetCrs, lenient)
      val srcDirectPosition2D: DirectPosition2D = new DirectPosition2D(
        sourceCrs, minx, miny)
      var destDirectPosition2D: DirectPosition2D = new DirectPosition2D()
      mathTransform.transform(srcDirectPosition2D, destDirectPosition2D)

      (destDirectPosition2D.y,destDirectPosition2D.x)

    } catch {
      case e:NoSuchAuthorityCodeException => {
        logger.error(s"$coords , $source_srs  " + e.getLocalizedMessage())
        (0.0,0.0)
      }
      case e:FactoryException => {
        logger.error(s"$coords , $source_srs  " + e.getLocalizedMessage())
        (0.0,0.0)
      }
      case e:MismatchedDimensionException => {
        logger.error(s"$coords , $source_srs  " + e.getLocalizedMessage())
        (0.0,0.0)
      }
      case e:TransformException => {
        logger.error(s"$coords , $source_srs  " + e.getLocalizedMessage())
        (0.0,0.0)
      }
    }

    retTuple

  }

}

package my.georef

import com.typesafe.scalalogging.slf4j.LazyLogging
import org.apache.spark.rdd.RDD

case class GeoName(name_id: Long, name: String) extends Serializable

case class Article(articleid: Long, title: String, textabs: String, fulltext: String, journal: String) extends Serializable

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

}

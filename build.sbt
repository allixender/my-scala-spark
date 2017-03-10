
name := """georef-spark-cassandra"""

version := "1.0-SNAPSHOT"

// lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.8"

val CassandraDriver = "3.0.2"
val CommonsIO       = "2.4"
val ScalaCheck      = "1.12.5"
val ScalaTest       = "2.2.6"
val Spark           = "2.0.2"
val Connector       = "2.0.0"
val n52xmlVersion = "2.1.0"
val xmlBeansVersion = "2.6.0"
val geotoolsVersion = "13.1" // 13.6, 14.5, 15.4, 16.2

libraryDependencies ++= Seq(
  "org.scalatest"       %%  "scalatest"      % ScalaTest,
  "org.scalacheck"      %% "scalacheck"      % ScalaCheck,
  "com.typesafe"        % "config"           % "1.3.1",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
  "commons-io" % "commons-io" % CommonsIO,
  // "com.datastax.cassandra" % "cassandra-driver-core" % CassandraDriver exclude("org.xerial.snappy", "snappy-java"),
  "com.datastax.cassandra" % "cassandra-driver-core" % CassandraDriver % "provided",
  "org.xerial.snappy"       	% "snappy-java"           % "1.1.2.6",
  // "org.apache.spark" %% "spark-core" % Spark,
  // "org.apache.spark" %% "spark-sql" % Spark,
  // "org.apache.spark" %% "spark-graphx" % Spark,
  // "org.apache.spark" %% "spark-mllib" % Spark,
  "com.datastax.spark" %% "spark-cassandra-connector" % Connector exclude("org.slf4j", "slf4j-api"),
  "com.vividsolutions" % "jts" % "1.13",
  "org.apache.xmlbeans" % "xmlbeans" % xmlBeansVersion,
  "org.apache.xmlbeans" % "xmlbeans-xpath" % xmlBeansVersion,
  // "org.n52.sensorweb" % "52n-xml-csw-v202" % n52xmlVersion,
  "info.smart-project" % "iso19139-xml" % "0.0.9",
  "org.geotools" % "gt-epsg-hsql" % geotoolsVersion,
  "org.geotools" % "gt-referencing" % geotoolsVersion
)

resolvers ++= Seq(
  "Local Maven Repository"  at "file://" + Path.userHome.absolutePath + "/.m2/repository",
  "Typesafe Repo"           at "http://repo.typesafe.com/typesafe/releases/",
  "Scalaz Bintray Repo" at "https://dl.bintray.com/scalaz/releases",
  "52North Releases" at "http://52north.org/maven/repo/releases/",
  "allixender maven" at "https://dl.bintray.com/allixender/maven2"
)


scalacOptions in ThisBuild ++= Seq(
  "-target:jvm-1.7",
  "-encoding", "UTF-8",
  "-deprecation", // warning and location for usages of deprecated APIs
  "-feature", // warning and location for usages of features that should be imported explicitly
  "-unchecked", // additional warnings where generated code depends on assumptions
  "-Xlint", // recommended additional warnings
  "-Ywarn-adapted-args", // Warn if an argument list is modified to match the receiver
  "-Ywarn-value-discard", // Warn when non-Unit expression results are unused
  "-Ywarn-inaccessible",
  "-Ywarn-dead-code",
  "-language:reflectiveCalls"
)

javacOptions in Compile ++= Seq(
  "-encoding", "UTF-8",
  "-source", "1.7",
  "-target", "1.7",
  "-g",
  "-Xlint:-path",
  "-Xlint:deprecation",
  "-Xlint:unchecked"
)

spName := "allixender/georef-spark-cassandra" // the name of your Spark Package

sparkVersion := Spark

sparkComponents ++= Seq("sql", "mllib", "graphx")

run in Compile := Defaults.runTask(fullClasspath in Compile, mainClass in (Compile, run), runner in (Compile, run)).evaluated

import sbt._
import Keys._
import sbtassembly.AssemblyPlugin.autoImport._

// assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false, includeDependency = false)

assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false)

assemblyMergeStrategy in assembly := {
  case PathList("org","aopalliance", xs @ _*) => MergeStrategy.last
  case PathList("javax", "inject", xs @ _*) => MergeStrategy.last
  case PathList("javax", "servlet", xs @ _*) => MergeStrategy.last
  case PathList("javax", "activation", xs @ _*) => MergeStrategy.last
  case PathList("com", "google", xs @ _*) => MergeStrategy.last
  case PathList("io", "netty", xs @ _*) => MergeStrategy.last
  case PathList("com", "esotericsoftware", xs @ _*) => MergeStrategy.last
  case PathList("com", "codahale", xs @ _*) => MergeStrategy.last
  case PathList("com", "yammer", xs @ _*) => MergeStrategy.last
  case PathList("org", "apache", "hadoop", "yarn", xs @ _ *) => MergeStrategy.last
  case PathList("org", "apache", "spark", xs @ _ *) => MergeStrategy.last
  case PathList("org", "apache", xs @ _*) => MergeStrategy.last
  case PathList("org", "fusesource", xs @ _ *) => MergeStrategy.last
  case "about.html" => MergeStrategy.rename
  case "META-INF/ECLIPSEF.RSA" => MergeStrategy.last
  case "META-INF/mailcap" => MergeStrategy.last
  case "META-INF/mimetypes.default" => MergeStrategy.last
  case "plugin.properties" => MergeStrategy.last
  case "META-INF/io.netty.versions.properties" => MergeStrategy.last
  case "log4j.properties" => MergeStrategy.last
  case "application.conf" => MergeStrategy.concat
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
  }



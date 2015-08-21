import AssemblyKeys._ // put this at the top of the file,leave the next line blank

assemblySettings

name := """my-spark-cassandra"""

version := "1.0-SNAPSHOT"

// lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.10.4"

libraryDependencies ++= Seq(
  "com.vividsolutions" % "jts" % "1.13",
  "com.typesafe"        % "config"           % "1.2.1",
  "com.typesafe.scala-logging" %% "scala-logging-slf4j" % "2.1.2",
  "org.scalatest"       %%  "scalatest"      % "2.2.0",
  "org.scalacheck"      %% "scalacheck"      % "1.11.1",
  "commons-io" % "commons-io" % "2.2",
  "com.datastax.cassandra" % "cassandra-driver-core" % "2.1.5" exclude("org.xerial.snappy", "snappy-java"),
  "org.xerial.snappy"       	% "snappy-java"           % "1.1.1.7",
  "org.apache.spark" %% "spark-core" % "1.3.1" % "provided",
  "com.datastax.spark" %% "spark-cassandra-connector" % "1.3.0" exclude("org.slf4j", "slf4j-api")
)

resolvers ++= Seq(
    "Local Maven Repository"  at "file://" + Path.userHome.absolutePath + "/.m2/repository",
    "NL4J Repository"         at "http://nativelibs4java.sourceforge.net/maven/",
    "maven2 dev repository"   at "http://download.java.net/maven/2",
    "Typesafe Repo"           at "http://repo.typesafe.com/typesafe/releases/",
    "spray repo"              at "http://repo.spray.io/",
    "sonatypeSnapshots"       at "http://oss.sonatype.org/content/repositories/snapshots",
    "Scalaz Bintray Repo" at "https://dl.bintray.com/scalaz/releases"
)

resolvers += "smart releases" at "http://dev.smart-project.info/artifactory/libs-release/"

resolvers += "smart snapshots" at "http://dev.smart-project.info/artifactory/libs-snapshot/"


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

mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
  {
    case PathList("javax", "servlet", xs @ _*) => MergeStrategy.last
    case PathList("org", "apache", xs @ _*) => MergeStrategy.last
    case PathList("com", "esotericsoftware", xs @ _*) => MergeStrategy.last
    case "about.html" => MergeStrategy.rename
    case x => old(x)
  }
}


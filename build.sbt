name := "mbgenre"

version := "1.0"

scalaVersion := "2.11.7"

libraryDependencies ++= {
  val akkaV = "2.3.11"
  val sprayV = "1.3.3"
  Seq(
    "io.spray" %% "spray-client" % sprayV,
    "io.spray" %% "spray-can" % sprayV,
    "io.spray" %% "spray-http" % sprayV,
    "io.spray" %% "spray-httpx" % sprayV,
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "net.virtual-void" %% "json-lenses" % "0.6.0",
    "org" % "jaudiotagger" % "2.0.3",
    "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test")
}



scalariformSettings
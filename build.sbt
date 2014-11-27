name := "mbgenre"

version := "1.0"

scalaVersion := "2.11.4"

libraryDependencies ++= {
  val akkaV = "2.3.6"
  val sprayV = "1.3.2"
  Seq(
    "io.spray" %% "spray-client" % sprayV,
    "io.spray" %% "spray-can" % sprayV,
    "io.spray" %% "spray-http" % sprayV,
    "io.spray" %% "spray-httpx" % sprayV,
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "net.virtual-void" %% "json-lenses" % "0.6.0",
    "org" % "jaudiotagger" % "2.0.3")
}
    
name := "mbgenre"

version := "1.0"

scalaVersion := "2.11.5"

libraryDependencies ++= {
  val akkaVersion = "2.3.6"
  val sprayVersion = "1.3.2"
  Seq(
    "io.spray" %% "spray-client" % sprayVersion,
    "io.spray" %% "spray-can" % sprayVersion,
    "io.spray" %% "spray-http" % sprayVersion,
    "io.spray" %% "spray-httpx" % sprayVersion,
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "net.virtual-void" %% "json-lenses" % "0.6.0",
    "org" % "jaudiotagger" % "2.0.3")
}


wartremoverErrors ++= Warts.allBut(Wart.Any)
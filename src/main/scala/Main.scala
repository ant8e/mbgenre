import akka.actor.ActorSystem
import spray.client.pipelining._
import spray.http.HttpHeaders._
import spray.http.MediaTypes._
import spray.http.{HttpEncodings, HttpRequest, Uri, _}
import spray.httpx.SprayJsonSupport
import spray.json.DefaultJsonProtocol._
import spray.json.JsObject
import spray.json.lenses.JsonLenses._

import scala.concurrent.Future


class MusicBrainzSupport(implicit val system: ActorSystem) extends SprayJsonSupport {


  implicit val disp = system.dispatcher

  case class ReleaseId(value: String)

  case class ReleaseGroupId(value: String)

  private val jspipeline: HttpRequest => Future[JsObject] =
    addHeader(`Accept-Encoding`(HttpEncodings.*)) ~>
      addHeader(Accept(`application/json`)) ~>
      sendReceive ~>
      unmarshal[JsObject]

  val releaseUri = Uri("http://musicbrainz.org/ws/2/release") withQuery("fmt" -> "json", "inc" -> "release-groups")
  val releaseGroupUri = Uri("http://musicbrainz.org/ws/2/release-group") withQuery("fmt" -> "json", "inc" -> "url-rels")

  def fetchRelease(id: ReleaseId): Future[JsObject] = jspipeline(Get(releaseUri.withPath(releaseUri.path / id.value)))

  def fetchReleaseGroup(id: ReleaseGroupId): Future[JsObject] = jspipeline(Get(releaseGroupUri.withPath(releaseGroupUri.path / id.value)))


  def releaseGroupIdOfRelease(releaseId: ReleaseId): Future[Option[ReleaseGroupId]] =
    fetchRelease(releaseId).map { x =>
      x.extract[String]("release-group".? / 'id).map(ReleaseGroupId)
    }
}


class AllMusicSupport(implicit val system: ActorSystem) {

  val mbSupport = new MusicBrainzSupport()

  import mbSupport._

  implicit val as= system
//  implicit val dispatcher = system.dispatcher
  val pipeline: HttpRequest => Future[String] =
    addHeader(`Accept-Encoding`(HttpEncodingRange.*)) ~> sendReceive ~> unmarshal[String]


  def allMusicUrlOfRelease(id: ReleaseId): Future[Option[String]] = {
    val allMusicUrl = 'relations / filter('type.is[String](_ == "allmusic")) / 'url / 'resource

    releaseGroupIdOfRelease(id).flatMap {
      case Some(i) => fetchReleaseGroup(i).map(x => x.extract[String](allMusicUrl).headOption)
      case _ => Future.successful(None)
    }
  }


  val allMusicGenreRegEx = """<a href="http://www.allmusic.com/genre/.*">(.*)</a>""".r

  def allMusicGenre(albumUrl: String): Future[Option[String]] = pipeline(Get(albumUrl))
    .map(x => (allMusicGenreRegEx findFirstMatchIn x).map(x => x.group(1)))
}

/**
  */
object Main extends App {


  val system = ActorSystem()
  val support: AllMusicSupport = new AllMusicSupport()(system)

  import support._
  import support.mbSupport._


  val id1: ReleaseId = ReleaseId("5000a285-b67e-4cfc-b54b-2b98f1810d2e")

  val res2: Future[Option[String]] = allMusicUrlOfRelease(id1).flatMap { case Some(s) => allMusicGenre(s)}

  // private val res: Future[Option[ReleaseGroupId]] = releaseGroupIdOfRelease(ReleaseId("5000a285-b67e-4cfc-b54b-2b98f1810d2e"))
  //  res.onSuccess {
  //    case Some(ReleaseGroupId(v)) =>
  //      println(s"release group id : ${v}")
  //      system.shutdown()
  //  }

  /* res.onFailure {
     case e: Throwable =>
       throw e
       system.shutdown()
   }*/

  res2.onSuccess {
    case Some(v) =>
      println(s"allmusic genre: $v")
      system.shutdown()
  }

  res2.onFailure {
    case e: Throwable =>
      throw e
      system.shutdown()
  }

}

import MusicBrainzSupport.{ ReleaseGroupId, ReleaseId }
import akka.actor.ActorRefFactory
import spray.client.pipelining._
import spray.http.HttpHeaders._
import spray.http.MediaTypes._
import spray.http.{ HttpEncodings, HttpRequest, Uri }
import spray.httpx.SprayJsonSupport
import spray.json.DefaultJsonProtocol._
import spray.json.JsObject
import spray.json.lenses.JsonLenses._

import scala.concurrent.Future

object MusicBrainzSupport {

  case class ReleaseId(value: String)

  case class ReleaseGroupId(value: String)

}

trait MusicBrainzSupport extends SprayJsonSupport {
  def system: ActorRefFactory

  private implicit val disp = system.dispatcher

  private val jspipeline: HttpRequest => Future[JsObject] =
    addHeader(`Accept-Encoding`(HttpEncodings.*)) ~>
      addHeader(Accept(`application/json`)) ~>
      sendReceive(system, system.dispatcher) ~>
      unmarshal[JsObject]

  private val releaseUri = Uri("http://musicbrainz.org/ws/2/release") withQuery ("fmt" -> "json", "inc" -> "release-groups")
  private val releaseGroupUri = Uri("http://musicbrainz.org/ws/2/release-group") withQuery ("fmt" -> "json", "inc" -> "url-rels")

  private def fetchRelease(id: ReleaseId): Future[JsObject] = jspipeline(Get(releaseUri.withPath(releaseUri.path / id.value)))

  private def fetchReleaseGroup(id: ReleaseGroupId): Future[JsObject] = jspipeline(Get(releaseGroupUri.withPath(releaseGroupUri.path / id.value)))

  def releaseGroupIdOfRelease(releaseId: ReleaseId): Future[Option[ReleaseGroupId]] =
    fetchRelease(releaseId).map { x =>
      x.extract[String]("release-group".? / 'id).map(ReleaseGroupId)
    }

  def allMusicUrlOfRelease(id: ReleaseId): Future[Option[String]] = {
    val allMusicUrl = 'relations / filter('type.is[String](_ == "allmusic")) / 'url / 'resource

    releaseGroupIdOfRelease(id).flatMap {
      case Some(i) => fetchReleaseGroup(i).map(x => x.extract[String](allMusicUrl).headOption)
      case _ => Future.successful(None)
    }
  }
}
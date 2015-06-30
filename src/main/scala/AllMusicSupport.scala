import akka.actor._
import spray.client.pipelining._
import spray.http.HttpHeaders._
import spray.http.{ HttpRequest, _ }

import scala.concurrent.Future

trait AllMusicSupport {
  def system: ActorRefFactory

  private implicit val disp = system.dispatcher
  val pipeline: HttpRequest => Future[String] =
    addHeader(`Accept-Encoding`(HttpEncodingRange.*)) ~> sendReceive(system, system.dispatcher) ~> unmarshal[String]

  val allMusicGenreRegEx = """<a href="http://www.allmusic.com/genre/.*">(.*)</a>""".r

  def allMusicGenre(albumUrl: String): Future[Option[String]] = pipeline(Get(albumUrl))
    .map(x => (allMusicGenreRegEx findFirstMatchIn x).map(x => x.group(1)))
}

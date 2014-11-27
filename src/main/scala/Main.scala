import MusicBrainzSupport.{ReleaseGroupId, ReleaseId}
import RunActor.{RequestToken, Requests, Response, Results}
import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import spray.client.pipelining._
import spray.http.HttpHeaders._
import spray.http.MediaTypes._
import spray.http.{HttpEncodings, HttpRequest, Uri, _}
import spray.httpx.SprayJsonSupport
import spray.json.DefaultJsonProtocol._
import spray.json.JsObject
import spray.json.lenses.JsonLenses._

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}


object MusicBrainzSupport {

  case class ReleaseId(value: String)

  case class ReleaseGroupId(value: String)

}

class MusicBrainzSupport(implicit val system: ActorSystem) extends SprayJsonSupport {


  implicit val disp = system.dispatcher

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

  implicit val as = system
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

object RunActor {

  import MusicBrainzSupport.ReleaseId

  case object RequestToken

  case class Requests(l: Seq[ReleaseId])

  case class Response(id: ReleaseId, genre: Option[String])

  case class Results(r: Map[ReleaseId, String])

}

class RunActor extends Actor {

  import context.become

  val support: AllMusicSupport = new AllMusicSupport()(context.system)

  import support.{allMusicGenre, allMusicUrlOfRelease}

  def receive = makeReceive(List.empty, Map.empty, 0, self)

  implicit val disp = context.system.dispatcher

  def runRequest(id: ReleaseId): Unit = allMusicUrlOfRelease(id).flatMap {
    case Some(url) => allMusicGenre(url)
    case None => Future.successful(None)
  }.onComplete {
    case Success(v) => self ! Response(id, v)
    case Failure(t) => self ! Response(id, None)

  }

  def makeReceive(requests: Seq[ReleaseId], results: Map[ReleaseId, String], runningRequests: Int, p: ActorRef): Actor.Receive = {
    case Requests(l) => become(makeReceive(requests.toList ::: l.toList, results, 0, sender()))
    case RequestToken => requests match {
      case h :: t => runRequest(h)
        become(makeReceive(t, results, runningRequests + 1, p))
      case _ =>
    }
    case Response(id, genre) => {
      val res = genre match {
        case Some(g) => results + (id -> g)
        case _ => results
      }
      become(makeReceive(requests, res, runningRequests - 1, p))
      if (requests.size > 0)
        self ! RequestToken
      else
      if (runningRequests == 1) {
        p ! Results(res)
        p ! PoisonPill
      }
    }
  }


}

object MainActor {
  def props(ids: Seq[ReleaseId]) = Props(new MainActor(ids))
}

class MainActor(val ids: Seq[ReleaseId]) extends Actor {
  val runner = context.actorOf(Props[RunActor])

  runner ! Requests(ids)
  for (i <- 1 to 5) {
    runner ! RequestToken
  }
  var results = Map[ReleaseId, String]()

  def receive = awaitAsk

  def awaitAsk: Actor.Receive = {
    case _ => context.become(awaitResult(sender()))
  }

  def awaitResult(a: ActorRef): Actor.Receive = {
    case Results(r) => a ! r
  }

}

/**
  */
object Main extends App {


  val system = ActorSystem()


  val id1: ReleaseId = ReleaseId("5000a285-b67e-4cfc-b54b-2b98f1810d2e")

  private val mainActorRef: ActorRef = system.actorOf(MainActor.props(List(id1)))


  import scala.concurrent.duration._
  implicit val timeout = Timeout (2.minutes)
  implicit val dispatcher = system.dispatcher
  val res2 = (mainActorRef ? "go").mapTo[Map[ReleaseId, String]]


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
    case x: Map[ReleaseId, String] => for ((k, v) <- x) {
      println(s"allmusic genre for $k : $v")
    }
      system.shutdown()
  }

  res2.onFailure {
    case e: Throwable =>
      throw e
      system.shutdown()
  }

}

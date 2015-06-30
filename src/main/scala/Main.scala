import java.io.File

import MusicBrainzSupport.{ ReleaseGroupId, ReleaseId }
import RunActor.{ RequestToken, Requests, Response, Results }
import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import org.jaudiotagger.audio.{ AudioFileIO, AudioFile }
import org.jaudiotagger.tag.FieldKey
import spray.client.pipelining._
import spray.http.HttpHeaders._
import spray.http.MediaTypes._
import spray.http.{ HttpEncodings, HttpRequest, Uri, _ }
import spray.httpx.SprayJsonSupport
import spray.json.DefaultJsonProtocol._
import spray.json.JsObject
import spray.json.lenses.JsonLenses._

import scala.concurrent.Future
import scala.io.Source
import scala.util.{ Try, Failure, Success }

object RunActor {

  import MusicBrainzSupport.ReleaseId

  case object RequestToken

  case class Requests(l: Seq[ReleaseId])

  case class Response(id: ReleaseId, genre: Option[String])

  case class Results(r: Map[ReleaseId, String])

}

class RunActor extends Actor with MusicBrainzSupport with AllMusicSupport with ActorLogging {

  import context.become
  import scala.concurrent.duration._
  private implicit val dispatcher = context.system.dispatcher

  context.system.scheduler.schedule(50.milli, 200.milli, self, RequestToken)

  def receive = behavior(List.empty, Map.empty, 0, self)

  def runRequest(id: ReleaseId): Unit = allMusicUrlOfRelease(id).flatMap {
    case Some(url) => allMusicGenre(url)
    case None => Future.successful(None)
  }.onComplete {
    case Success(v) => self ! Response(id, v)
    case Failure(t) => self ! Response(id, None)
  }

  def behavior(requests: Seq[ReleaseId], results: Map[ReleaseId, String], runningRequests: Int, p: ActorRef): Actor.Receive = {
    case Requests(l) =>
      log.debug(s"received Requests : $l")
      become(behavior(requests.toList ::: l.toList, results, runningRequests, sender()))
    case RequestToken =>
      log.debug(s"received token")
      requests match {
        case h :: t =>
          log.debug(s"running request for $h")
          runRequest(h)
          become(behavior(t, results, runningRequests + 1, p))
        case _ =>
      }
    case Response(id, genre) => {
      log.debug(s"received response $id -> $genre")
      val res = genre match {
        case Some(g) => results + (id -> g)
        case _ => results
      }
      become(behavior(requests, res, runningRequests - 1, p))

      if (requests.size == 0 && runningRequests == 1) {
        log.debug("Sending results")
        p ! Results(res)
        p ! PoisonPill
      }
    }
  }

  override def system(): ActorRefFactory = context.system
}

object MainActor {
  def props(ids: Seq[ReleaseId]) = Props(new MainActor(ids))
}

class MainActor(val ids: Seq[ReleaseId]) extends Actor with ActorLogging {
  val runner = context.actorOf(Props[RunActor])
  log.debug("Runner actor created")

  runner ! Requests(ids)

  var results = Map[ReleaseId, String]()

  def receive = awaitAsk

  def awaitAsk: Actor.Receive = {
    case _ => context.become(awaitResult(sender()))
  }

  def awaitResult(a: ActorRef): Actor.Receive = {
    case Results(r) =>
      log.debug("got results :" + r)
      a ! r
  }

}

trait ReadTagSupport {

  def readMusicBrainzReleaseId(f: File): Try[Option[String]] = Try {
    val tag = AudioFileIO.read(f).getTag.getFirst(FieldKey.MUSICBRAINZ_RELEASEID)
    if (tag.isEmpty) None else Some(tag)
  }

  def isAudioFile(f: File): Boolean = Try {
    AudioFileIO.read(f)
  }.isSuccess

  def listAllAudioFiles(directory: File): List[File] = if (!directory.isDirectory)
    Nil
  else {
    val current: List[File] = directory.listFiles().toList
    current.filter(isAudioFile) ++ current.filter(_.isDirectory).flatMap(listAllAudioFiles)
  }

}

object ReadTagSupport extends ReadTagSupport

/**
 */
object Main extends App {

  println(args.mkString)

  if (args.length < 1) {
    println("""Usage : mbgenre <directory> """)
    System.exit(1)
  }

  val dir = new File(args(0))

  private val ids: List[ReleaseId] =
    ReadTagSupport.listAllAudioFiles(dir)
      .map(ReadTagSupport.readMusicBrainzReleaseId)
      .collect { case Success(Some(id)) => ReleaseId(id) }
      .distinct

  //  private val input: List[ReleaseId] = Source.fromInputStream(System.in).getLines().map(ReleaseId).toList

  val id1: ReleaseId = ReleaseId("5000a285-b67e-4cfc-b54b-2b98f1810d2e")
  val id2: ReleaseId = ReleaseId("73993bc4-901e-4706-a25a-5d08aa044893")

  //  val ids = List(id1,id2)

  if (ids.isEmpty)
    println("no mb audio file found")
  else
    process(ids)

  def process(ids: List[ReleaseId]) = {
    val system = ActorSystem()

    val mainActorRef: ActorRef = system.actorOf(MainActor.props(ids))

    import scala.concurrent.duration._

    implicit val timeout = Timeout(2.minutes)
    implicit val dispatcher = system.dispatcher
    val res2 = (mainActorRef ? "go").mapTo[Map[ReleaseId, String]]

    res2.onComplete {
      case Success(x) =>
        for ((k, v) <- x) {
          println(s"allmusic genre for $k : $v")
        }
        system.shutdown()
      case Failure(e) =>
        throw e
        system.shutdown()
    }
  }
}

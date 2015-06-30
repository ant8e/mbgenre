import MusicBrainzSupport.{ ReleaseGroupId, ReleaseId }
import akka.actor.{ ActorSystem, ActorRefFactory }
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfter, FunSpec }

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._

class MusicBrainzSupportSpec extends FunSpec with BeforeAndAfterAll {
  val actorSystem = ActorSystem()

  // Ok Computer : https://musicbrainz.org/release/541a0976-ca45-3c0f-89e5-26bc376f58d1
  val okComputerRid = ReleaseId("541a0976-ca45-3c0f-89e5-26bc376f58d1")
  val okComputerRGId = ReleaseGroupId("b1392450-e666-3926-a536-22c65f834433")
  val okComputerAllMusicUlr = "http://www.allmusic.com/album/mw0000024289"

  object MusicBrainzbSupportTest extends MusicBrainzSupport {
    override def system = actorSystem
  }

  describe("Music Brain Support") {
    it("should retrieve ReleaseGroupId") {
      val f = MusicBrainzbSupportTest.releaseGroupIdOfRelease(okComputerRid)
      val r = Await.result(f, 10.seconds)
      assert(r.isDefined)
      assert(r.get == okComputerRGId)
    }
    it("should retrieve AllMusic url for release") {
      val f = MusicBrainzbSupportTest.allMusicUrlOfRelease(okComputerRid)
      val r = Await.result(f, 10.seconds)
      assert(r.isDefined)
      assert(r.get == okComputerAllMusicUlr)
    }
  }

  override protected def afterAll(): Unit = {
    actorSystem.shutdown()
  }
}

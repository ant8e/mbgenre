import MusicBrainzSupport.{ReleaseGroupId, ReleaseId}
import akka.actor.{ActorRefFactory, ActorSystem}
import org.scalatest.{BeforeAndAfterAll, FunSpec}

import scala.concurrent.Await
import scala.concurrent.duration._

class AllMusicSupportSpec extends FunSpec with BeforeAndAfterAll{
  val actorSystem = ActorSystem()

  // Ok Computer : https://musicbrainz.org/release/541a0976-ca45-3c0f-89e5-26bc376f58d1
  val okComputerAllMusicUlr= "http://www.allmusic.com/album/mw0000024289"

  object AllMusicSupportTest extends AllMusicSupport  with ActorRefFactoryProvider                    {
    override def system(): ActorRefFactory = actorSystem
  }

  describe("All Music Support"){
    it("should retrieve Genre"){
      val f =  AllMusicSupportTest.allMusicGenre(okComputerAllMusicUlr)
      val r = Await.result(f, 10.seconds)
      assert(r.isDefined)
      assert (r.get =="Pop/Rock")
    }

  }

  override protected def afterAll(): Unit = {
    actorSystem.shutdown()
  }
}

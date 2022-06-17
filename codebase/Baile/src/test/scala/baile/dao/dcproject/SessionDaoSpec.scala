package baile.dao.dcproject

import java.time.Instant

import baile.BaseSpec
import baile.domain.dcproject.Session
import org.mongodb.scala.MongoDatabase

import scala.util.Success

class SessionDaoSpec extends BaseSpec {

  val session = Session(
    geminiSessionId = randomString(),
    geminiSessionToken = randomString(),
    geminiSessionUrl = randomString(),
    dcProjectId = randomString(),
    created = Instant.now()
  )

  "SessionDao" should {
    val mockedMongoDatabase: MongoDatabase = mock[MongoDatabase]
    val dao = new SessionDao(mockedMongoDatabase)

    "convert session to document and back" in {
      val document = dao.entityToDocument(session)
      val sessionEntity = dao.documentToEntity(document)
      sessionEntity shouldBe Success(session)
    }
  }

}

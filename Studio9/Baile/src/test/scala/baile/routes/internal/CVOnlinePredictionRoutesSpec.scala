package baile.routes.internal

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.server.Route
import baile.routes.RoutesSpec
import baile.services.cv.online.CVOnlinePredictionService
import baile.services.cv.online.CVOnlinePredictionService.{ AlbumNotForPredictionError, AlbumNotFoundError }
import cortex.api.baile.PredictionResultItem
import org.mockito.ArgumentMatchers.{ any, eq => eqTo }
import org.mockito.Mockito._
import play.api.libs.json.{ JsArray, JsNumber, JsObject, JsString }

class CVOnlinePredictionRoutesSpec extends RoutesSpec {
  private val service = mock[CVOnlinePredictionService]

  val routes: Route = new CVOnlinePredictionRoutes(conf, service).routes

  private val albumId = randomString()
  private val nonPredictionAlbumId = randomString()
  private val validCreds = BasicHttpCredentials(
    conf.getString("private-http.username"),
    conf.getString("private-http.password")
  )

  when(service.insertPredictedPictures(any[String], any[Seq[PredictionResultItem]]))
    .thenReturn(future(Left(AlbumNotFoundError(randomString()))))
  when(service.insertPredictedPictures(eqTo(nonPredictionAlbumId), any[Seq[PredictionResultItem]]))
    .thenReturn(future(Left(AlbumNotForPredictionError(randomString()))))
  when(service.insertPredictedPictures(eqTo(albumId), any[Seq[PredictionResultItem]]))
    .thenReturn(future(Right(())))

  "POST /cv-online-prediction/{albumId}" should {
    "accept correct request" in {
      val uploadRequest = JsObject(Map(
        "albumId" -> JsString(albumId),
        "results" -> JsArray(Seq(
          JsObject(Map(
            "filePath" -> JsString(randomPath("png")),
            "fileSize" -> JsNumber(randomInt(1024, 102400)),
            "fileName" -> JsString(randomPath("png")),
            "metadata" -> JsObject.empty,
            "label" -> JsString(randomString()),
            "confidence" -> JsNumber(randomInt(0, 1000) / 1000.0)
          ))
        ))
      ))

      Post(s"/cv-online-prediction/$albumId", uploadRequest)
        .withCredentials(validCreds)
        .check {
          status shouldBe StatusCodes.OK
        }
    }

    "reject request with unknown album id" in {
      val randomAlbumId = randomString()

      val uploadRequest = JsObject(Map(
        "albumId" -> JsString(randomAlbumId),
        "results" -> JsArray(Seq(
          JsObject(Map(
            "filePath" -> JsString(randomPath("png")),
            "fileSize" -> JsNumber(randomInt(1024, 102400)),
            "fileName" -> JsString(randomPath("png")),
            "metadata" -> JsObject.empty,
            "label" -> JsString(randomString()),
            "confidence" -> JsNumber(randomInt(0, 1000) / 1000.0)
          ))
        ))
      ))

      Post(s"/cv-online-prediction/$randomAlbumId", uploadRequest)
        .withCredentials(validCreds)
        .check {
          status shouldBe StatusCodes.NotFound
          validateErrorResponse(responseAs[JsObject])
        }
    }

    "reject request with non prediction album id" in {
      val uploadRequest = JsObject(Map(
        "albumId" -> JsString(nonPredictionAlbumId),
        "results" -> JsArray(Seq(
          JsObject(Map(
            "filePath" -> JsString(randomPath("png")),
            "fileSize" -> JsNumber(randomInt(1024, 102400)),
            "fileName" -> JsString(randomPath("png")),
            "metadata" -> JsObject.empty,
            "label" -> JsString(randomString()),
            "confidence" -> JsNumber(randomInt(0, 1000) / 1000.0)
          ))
        ))
      ))

      Post(s"/cv-online-prediction/$nonPredictionAlbumId", uploadRequest)
        .withCredentials(validCreds)
        .check {
          status shouldBe StatusCodes.BadRequest
          validateErrorResponse(responseAs[JsObject])
        }
    }
  }
}


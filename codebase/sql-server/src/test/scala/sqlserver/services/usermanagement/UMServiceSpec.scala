package sqlserver.services.usermanagement

import java.util.UUID

import akka.actor.ExtendedActorSystem
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model.{ HttpResponse, StatusCodes }
import play.api.libs.json._
import sqlserver.BaseSpec
import sqlserver.RandomGenerators._
import sqlserver.services.usermanagement.datacontract.UserResponse
import cats.implicits._
import sqlserver.services.usermanagement.UMService.TokenValidationError.InvalidToken

import scala.concurrent.duration._

class UMServiceSpec extends BaseSpec {

  trait Setup {

    val settings = UMService.Settings(
      baseUrl = "http://um-service.url",
      responseTimeout = 10.seconds,
      firstRetryDelay = 1.seconds,
      retriesCount = 1
    )
    val http = mock[HttpExt]
    val extendedActorSystem: ExtendedActorSystem = mock[ExtendedActorSystem]

    val service = new UMService(settings, http)

    val token = randomString()

    val user = UserResponse(
      UUID.randomUUID(),
      "john.doe",
      "john.doe@example.com",
      "John",
      "Doe"
    )

    val rawUser = JsObject(Seq(
      "id" -> Json.toJson(user.id),
      "username" -> JsString(user.username),
      "email" -> JsString(user.email),
      "firstName" -> JsString(user.firstName),
      "lastName" -> JsString(user.lastName)
    ))
  }

  "UMService#validateAccessToken" should {

    "return a user if token is valid" in new Setup {
      http.system shouldReturn extendedActorSystem
      extendedActorSystem.log shouldReturn logger
      http.defaultClientHttpsContext shouldReturn null // scalastyle:off null
      http.singleRequest(*, *, *, *) shouldReturn future(HttpResponse(entity = httpEntity(rawUser)))

      whenReady(service.validateAccessToken(token))(_ shouldBe user.asRight)
    }

    "return an error if token is empty" in new Setup {
      whenReady(service.validateAccessToken(""))(_ shouldBe InvalidToken.asLeft)
    }

    "return an error if token is invalid" in new Setup {
      http.system shouldReturn extendedActorSystem
      extendedActorSystem.log shouldReturn logger
      http.defaultClientHttpsContext shouldReturn null // scalastyle:off null
      http.singleRequest(*, *, *, *) shouldReturn future(HttpResponse(status = StatusCodes.BadRequest))

      whenReady(service.validateAccessToken(token))(_ shouldBe InvalidToken.asLeft)
    }

  }
}

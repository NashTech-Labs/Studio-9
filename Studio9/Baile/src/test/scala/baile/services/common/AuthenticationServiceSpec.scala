package baile.services.common

import baile.ExtendedBaseSpec
import baile.services.process.ProcessService
import baile.services.usermanagement.UmService
import baile.services.usermanagement.util.TestData.SampleUser
import baile.RandomGenerators._
import baile.domain.usermanagement.{ AccessToken, ExperimentExecutor }
import baile.services.usermanagement.UmService.{ GetUserError, SignInError }
import baile.services.process.ProcessService.ProcessNotFoundError
import baile.services.process.util.ProcessRandomGenerator
import cats.implicits._

class AuthenticationServiceSpec extends ExtendedBaseSpec {

  trait Setup {
    val umService = mock[UmService]
    val processService = mock[ProcessService]

    val service = new AuthenticationService(
      umService,
      processService
    )

    val user = SampleUser
    val token = randomString()
  }

  "AuthenticationService#authenticate(token)" should {

    "return user when token is valid in um service" in new Setup {
      umService.validateAccessToken(token) shouldReturn future(user.asRight)

      whenReady(service.authenticate(token))(_ shouldBe Some(user))
    }

    "return experiment executor when token is found in processes" in new Setup {
      val process = ProcessRandomGenerator.randomProcess(ownerId = user.id)
      val experimentExecutor = ExperimentExecutor(
        user.id,
        user.username,
        user.email,
        user.firstName,
        user.lastName,
        process.entity.targetId,
        user.permissions,
        user.role
      )
      umService.validateAccessToken(token) shouldReturn future(GetUserError.UserNotFound.asLeft)
      processService.getActiveProcessByAuthToken(token) shouldReturn future(process.asRight)
      umService.getUserMandatory(user.id) shouldReturn future(user)

      whenReady(service.authenticate(token))(_ shouldBe Some(experimentExecutor))
    }

    "return none when token is neither valid in um service no found in processes" in new Setup {
      umService.validateAccessToken(token) shouldReturn future(GetUserError.UserNotFound.asLeft)
      processService.getActiveProcessByAuthToken(token) shouldReturn future(ProcessNotFoundError.asLeft)

      whenReady(service.authenticate(token))(_ shouldBe None)
    }

  }

  "AuthenticationService#authenticate(username, password)" should {

    val password = randomString()

    "return user when credentials are correct" in new Setup {
      umService.signIn(user.username, password) shouldReturn future(AccessToken(token, randomInt(5), "simple").asRight)
      umService.validateAccessToken(token) shouldReturn future(user.asRight)

      whenReady(service.authenticate(user.username, password))(_ shouldBe Some(user))
    }

    "authenticate by token when username is empty" in new Setup {
      umService.validateAccessToken(token) shouldReturn future(user.asRight)

      whenReady(service.authenticate("", token))(_ shouldBe Some(user))
    }

    "return none when credentials are incorrect" in new Setup {
      umService.signIn(user.username, password) shouldReturn future(SignInError.InvalidCredentials.asLeft)

      whenReady(service.authenticate(user.username, password))(_ shouldBe None)
    }

  }

}

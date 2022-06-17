package argo.rest.config

import java.util.{ Date, UUID }

import akka.http.scaladsl.model.StatusCodes
import akka.testkit.TestProbe
import argo.common.json4s.ArgoJson4sSupport
import argo.common.service.DateImplicits._
import argo.domain.rest.config.{ ConfigSettingContract, CreateUpdateConfigSettingContract }
import argo.domain.service.config._
import argo.testkit.BaseSpec
import argo.testkit.rest.HttpEndpointBaseSpec

class ConfigSettingHttpEndpointSpec extends HttpEndpointBaseSpec {

  // Fixtures
  val configSettingId = UUID.randomUUID()
  val serviceName = "online-prediction"
  val settingName = "s3.input.bucket"
  val settingValue = "test-input.deepcortex.ai"
  val configSettingTags = List("jobA")
  val currentDate = new Date().withoutMillis()
  val createdAt = currentDate
  val updatedAt = currentDate

  val createUpdateConfigSetting = CreateUpdateConfigSetting(serviceName, settingName, settingValue, configSettingTags)
  val createUpdateConfigSettingContract = CreateUpdateConfigSettingContract(settingValue, configSettingTags)

  val configSetting = ConfigSetting(configSettingId, serviceName, settingName, settingValue, configSettingTags, createdAt, updatedAt)
  val configSettingContract = ConfigSettingContract(serviceName, settingName, settingValue, configSettingTags, createdAt, updatedAt)

  val nonExistentConfigSettingName = "non-existent-setting-name"

  trait Scope extends RouteScope with ArgoJson4sSupport {
    val configSettingCommandService = TestProbe()
    val configSettingQueryService = TestProbe()

    val configSettingHttpEndpoint = new ConfigSettingHttpEndpoint(
      configSettingCommandService.ref,
      configSettingQueryService.ref,
      testAppConfig
    )

    val routes = configSettingHttpEndpoint.routes

  }

  "When sending a PUT request, the ConfigSetting API" should {
    "create or update the ConfigSetting and return a 200 Response with the requested ConfigSetting if it exists" in new Scope {
      val result = Put(s"/services/$serviceName/config-settings/$settingName", createUpdateConfigSettingContract).run

      configSettingCommandService.expectMsg(createUpdateConfigSetting)
      configSettingCommandService.reply(configSetting)

      result.check {
        response.status shouldBe StatusCodes.OK
        responseAs[ConfigSettingContract] shouldBe configSettingContract
      }
    }
    "not create a ConfigSetting and return a 400 Response if the request is not valid" in new Scope {
      val settingValue = "" // settingValue must not be empty
      val result = Put(s"/services/$serviceName/config-settings/$settingName", Map("settingValue" -> settingValue)).runSeal

      configSettingCommandService.expectNoMsg()

      result.check {
        response.status shouldBe StatusCodes.BadRequest
      }
    }
  }

  "When sending a GET request, the ConfigSetting API" should {
    "return a 200 Response with the requested ConfigSetting if it exists" in new Scope {
      val result = Get(s"/services/$serviceName/config-settings/$settingName").run

      configSettingQueryService.expectMsg(RetrieveConfigSetting(serviceName, settingName))
      configSettingQueryService.reply(Some(configSetting))

      result.check {
        response.status shouldBe StatusCodes.OK
        responseAs[ConfigSettingContract] shouldBe configSettingContract
      }
    }
    "return a 404 Response if the requested ConfigSetting does not exist" in new Scope {
      val result = Get(s"/services/$serviceName/config-settings/$nonExistentConfigSettingName").runSeal

      configSettingQueryService.expectMsg(RetrieveConfigSetting(serviceName, nonExistentConfigSettingName))
      configSettingQueryService.reply(None)

      result.check {
        response.status shouldBe StatusCodes.NotFound
      }
    }
  }

  "When sending a DELETE request, the ConfigSetting API" should {
    "delete the requested ConfigSetting and return a 200 Response with the requested ConfigSetting if it exists" in new Scope {
      val result = Delete(s"/services/$serviceName/config-settings/$settingName").run

      configSettingCommandService.expectMsg(DeleteConfigSetting(serviceName, settingName))
      configSettingCommandService.reply(Some(configSetting))

      result.check {
        response.status shouldBe StatusCodes.OK
        responseAs[ConfigSettingContract] shouldBe configSettingContract
      }
    }
    "return a 404 Response if the requested ConfigSetting does not exist" in new Scope {
      val result = Delete(s"/services/$serviceName/config-settings/$nonExistentConfigSettingName").runSeal

      configSettingCommandService.expectMsg(DeleteConfigSetting(serviceName, nonExistentConfigSettingName))
      configSettingCommandService.reply(None)

      result.check {
        response.status shouldBe StatusCodes.NotFound
      }
    }
  }

  "When sending a GET request, the ConfigSetting API" should {
    "return a list of all ConfigSetting" in new Scope {
      val allConfigSettingEntities = Seq(configSetting, configSetting.copy(id = UUID.randomUUID(), setting_name = "some-other-setting-name"))
      val allConfigSettingContracts = Seq(configSettingContract, configSettingContract.copy(settingName = "some-other-setting-name"))

      val result = Get(s"/services/$serviceName/config-settings").run

      configSettingQueryService.expectMsg(RetrieveConfigSettings(serviceName))
      configSettingQueryService.reply(allConfigSettingEntities)

      result.check {
        response.status shouldBe StatusCodes.OK
        responseAs[Seq[ConfigSettingContract]] should contain theSameElementsAs allConfigSettingContracts
      }
    }
  }

}

class ConfigSettingHttpEndpointTranslatorsSpec extends BaseSpec {
  import ConfigSettingHttpEndpoint._

  // Fixtures
  val configSettingId = UUID.randomUUID()
  val serviceName = "online-prediction"
  val settingName = "s3.input.bucket"
  val settingValue = "test-input.deepcortex.ai"
  val configSettingTags = List("jobA")
  val currentDate = new Date()
  val createdAt = currentDate
  val updatedAt = currentDate

  val createUpdateConfigSettingContract = CreateUpdateConfigSettingContract(settingValue, configSettingTags)
  val createUpdateConfigSetting = CreateUpdateConfigSetting(serviceName, settingName, settingValue, configSettingTags)

  val configSetting = ConfigSetting(configSettingId, serviceName, settingName, settingValue, configSettingTags, createdAt, updatedAt)
  val configSettingContract = ConfigSettingContract(serviceName, settingName, settingValue, configSettingTags, createdAt, updatedAt)

  "ConfigSetting To ConfigSettingContract translator" should {
    "translate ConfigSetting to ConfigSettingContract" in {
      val result = toConfigSettingContract.translate(configSetting)
      result shouldBe configSettingContract
    }
  }
}


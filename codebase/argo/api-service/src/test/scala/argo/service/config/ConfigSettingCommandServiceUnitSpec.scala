package argo.service.config

import java.util.UUID

import akka.testkit.{ TestActorRef, TestProbe }
import argo.common.elastic.ElasticSearchRepository.DeleteResult
import argo.domain.service.config._
import argo.testkit.service.{ DateSupportMocks, ServiceBaseSpec, UUIDSupportMocks }

import scala.concurrent.Future

class ConfigSettingCommandServiceUnitSpec extends ServiceBaseSpec with DateSupportMocks with UUIDSupportMocks {

  // Fixtures
  val configSettingId = mockRandomUUID
  val serviceName = "online-prediction"
  val settingName = "s3.input.bucket"
  val settingValue = "test-input.deepcortex.ai"
  val configSettingTags = List("jobA")
  val createdAt = mockCurrentDate
  val updatedAt = mockCurrentDate

  val createUpdateConfigSetting = CreateUpdateConfigSetting(serviceName, settingName, settingValue, configSettingTags)
  val configSetting = ConfigSetting(configSettingId, serviceName, settingName, settingValue, configSettingTags, createdAt, updatedAt)

  val nonExistentConfigSettingId = UUID.randomUUID()

  trait Scope extends ServiceScope {
    val configSettingQueryServiceProbe = TestProbe()
    val mockRepository = mock[ConfigSettingRepository]

    val service = TestActorRef(new ConfigSettingCommandService(configSettingQueryServiceProbe.ref) with UUIDSupportTesting with DateSupportTesting {
      override val repository = mockRepository
    })
  }

  "When receiving a CreateUpdateConfigSetting msg, the service" should {
    "create a new ConfigSetting if it does not exist" in new Scope {
      // Mock Repository call
      (mockRepository.create _).expects(configSetting).returning(Future.successful(configSetting))

      // Send msg
      service ! createUpdateConfigSetting

      configSettingQueryServiceProbe.expectMsg(RetrieveConfigSetting(serviceName, settingName))
      configSettingQueryServiceProbe.reply(None)

      // Verify service response
      expectMsg(configSetting)
    }
    "update the ConfigSetting if it does exist already" in new Scope {
      val updatedSettingValue = "updated-setting-value"
      val updatedTags = List("updated-tag")

      val updateConfigSettingData = createUpdateConfigSetting.copy(settingValue = updatedSettingValue, tags = updatedTags)
      val updatedConfigSetting = configSetting.copy(setting_value = updatedSettingValue, tags = updatedTags)

      // Mock Repository call
      (mockRepository.update _).expects(configSettingId, updatedConfigSetting).returning(Future.successful(Some(updatedConfigSetting)))

      // Send msg
      service ! updateConfigSettingData

      configSettingQueryServiceProbe.expectMsg(RetrieveConfigSetting(serviceName, settingName))
      configSettingQueryServiceProbe.reply(Some(configSetting))

      // Verify service response
      expectMsg(updatedConfigSetting)
    }
    "respond with a Failure msg if the call for finding the ConfigSetting fails" in new Scope {
      val findException = new RuntimeException("BOOM!")

      // Mock Repository call
      (mockRepository.create _).expects(configSetting).never()
      (mockRepository.update _).expects(configSettingId, configSetting).never()

      // Send msg
      service ! createUpdateConfigSetting

      configSettingQueryServiceProbe.expectMsg(RetrieveConfigSetting(serviceName, settingName))
      configSettingQueryServiceProbe.replyWithFailure(findException)

      // Verify service response
      expectMsgFailure(findException)
    }
    "respond with a Failure msg if the call for creating the ConfigSetting in ElasticSearch fails" in new Scope {
      val elasticException = new RuntimeException("BOOM!")

      // Mock Repository call
      (mockRepository.create _).expects(configSetting).returning(Future.failed(elasticException))

      // Send msg
      service ! createUpdateConfigSetting

      configSettingQueryServiceProbe.expectMsg(RetrieveConfigSetting(serviceName, settingName))
      configSettingQueryServiceProbe.reply(None)

      // Verify service response
      expectMsgFailure(elasticException)
    }
    "respond with a Failure msg if the call for updating the ConfigSetting returns an empty response" in new Scope {
      val updatedSettingValue = "updated-setting-value"
      val updatedTags = List("updated-tag")

      val updateConfigSettingData = createUpdateConfigSetting.copy(settingValue = updatedSettingValue, tags = updatedTags)
      val updatedConfigSetting = configSetting.copy(setting_value = updatedSettingValue, tags = updatedTags)

      // Mock Repository call
      (mockRepository.update _).expects(configSettingId, updatedConfigSetting).returning(Future.successful(None))

      // Send msg
      service ! updateConfigSettingData

      configSettingQueryServiceProbe.expectMsg(RetrieveConfigSetting(serviceName, settingName))
      configSettingQueryServiceProbe.reply(Some(configSetting))

      // Verify service response
      expectMsgFailurePF {
        case e: Exception => e
      }
    }

  }

  "When receiving a DeleteConfigSetting msg, the service" should {
    "delete the ConfigSetting and respond with it if it exists" in new Scope {
      // Mock Repository call
      (mockRepository.delete _).expects(configSettingId).returning(Future.successful(DeleteResult(true)))

      // Send msg
      service ! DeleteConfigSetting(serviceName, settingName)

      configSettingQueryServiceProbe.expectMsg(RetrieveConfigSetting(serviceName, settingName))
      configSettingQueryServiceProbe.reply(Some(configSetting))

      // Verify service response
      expectMsg(Some(configSetting))
    }
    "respond with None if the ConfigSetting does not exist upon trying to deleting it" in new Scope {
      // Mock Repository call
      (mockRepository.delete _).expects(configSettingId).returning(Future.successful(DeleteResult(false)))

      // Send msg
      service ! DeleteConfigSetting(serviceName, settingName)

      configSettingQueryServiceProbe.expectMsg(RetrieveConfigSetting(serviceName, settingName))
      configSettingQueryServiceProbe.reply(Some(configSetting))

      // Verify service response
      expectMsg(None)
    }
    "respond with None if the ConfigSetting does not exist when first looking up it" in new Scope {
      // Mock Repository call
      (mockRepository.delete _).expects(configSettingId).never()

      // Send msg
      service ! DeleteConfigSetting(serviceName, settingName)
      configSettingQueryServiceProbe.expectMsg(RetrieveConfigSetting(serviceName, settingName))
      configSettingQueryServiceProbe.reply(None)

      // Verify service response
      expectMsg(None)
    }
    "respond with a Failure msg if the call for finding the ConfigSetting fails" in new Scope {
      val findException = new RuntimeException("BOOM!")

      // Mock Repository call
      (mockRepository.delete _).expects(configSettingId).never()

      // Send msg
      service ! DeleteConfigSetting(serviceName, settingName)

      configSettingQueryServiceProbe.expectMsg(RetrieveConfigSetting(serviceName, settingName))
      configSettingQueryServiceProbe.replyWithFailure(findException)

      // Verify service response
      expectMsgFailure(findException)
    }
    "respond with a Failure msg if the call to ElasticSearch for deleting the ConfigSetting fails" in new Scope {
      val elasticException = new RuntimeException("BOOM!")

      // Mock Repository call
      (mockRepository.delete _).expects(configSettingId).returning(Future.failed(elasticException))

      // Send msg
      service ! DeleteConfigSetting(serviceName, settingName)

      configSettingQueryServiceProbe.expectMsg(RetrieveConfigSetting(serviceName, settingName))
      configSettingQueryServiceProbe.reply(Some(configSetting))

      // Verify service response
      expectMsgFailure(elasticException)
    }
  }
}

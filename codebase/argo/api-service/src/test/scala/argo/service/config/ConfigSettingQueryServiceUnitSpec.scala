package argo.service.config

import java.util.{ Date, UUID }

import akka.testkit.TestActorRef
import argo.common.elastic.ElasticSearchRepository
import argo.domain.service.config.{ ConfigSetting, RetrieveConfigSetting, RetrieveConfigSettings }
import argo.testkit.service.ServiceBaseSpec
import com.sksamuel.elastic4s.http.ElasticDsl.termQuery
import com.sksamuel.elastic4s.searches.sort.SortOrder

import scala.concurrent.Future

class ConfigSettingQueryServiceUnitSpec extends ServiceBaseSpec {

  // Fixtures
  val configSettingId = UUID.randomUUID()
  val serviceName = "online-prediction"
  val settingName = "s3.input.bucket"
  val settingValue = "test-input.deepcortex.ai"
  val configSettingTags = List("jobA")
  val createdAt = new Date()
  val updatedAt = new Date()

  val configSetting = ConfigSetting(configSettingId, serviceName, settingName, settingValue, configSettingTags, createdAt, updatedAt)

  trait Scope extends ServiceScope {
    val mockRepository = mock[ConfigSettingRepository]

    val service = TestActorRef(new ConfigSettingQueryService {
      override val repository = mockRepository
    })
  }

  "When receiving a RetrieveConfigSetting msg, the service" should {
    "respond with the ConfigSetting if found" in new Scope {
      // Mock Repository call
      val expectedQueries = Seq(termQuery("service_name", serviceName), termQuery("setting_name", settingName))
      (mockRepository.search _)
        .expects(expectedQueries, "setting_name", SortOrder.ASC, 1)
        .returning(Future.successful(Seq(configSetting)))

      service ! RetrieveConfigSetting(serviceName, settingName)

      // Verify service response
      expectMsg(Some(configSetting))
    }
    "respond with None if the ConfigSetting does not exist" in new Scope {
      // Mock Repository call
      val expectedQueries = Seq(termQuery("service_name", serviceName), termQuery("setting_name", settingName))
      (mockRepository.search _)
        .expects(expectedQueries, "setting_name", SortOrder.ASC, 1)
        .returning(Future.successful(Seq.empty))

      service ! RetrieveConfigSetting(serviceName, settingName)

      // Verify service response
      expectMsg(None)
    }
    "respond with Failure if the call to ElasticSearch fails" in new Scope {
      val elasticException = new RuntimeException("BOOM!")

      // Mock Repository call
      val expectedQueries = Seq(termQuery("service_name", serviceName), termQuery("setting_name", settingName))
      (mockRepository.search _)
        .expects(expectedQueries, "setting_name", SortOrder.ASC, 1)
        .returning(Future.failed(elasticException))

      service ! RetrieveConfigSetting(serviceName, settingName)

      // Verify service response
      expectMsgFailure(elasticException)
    }
  }

  "When receiving a RetrieveConfigSettings msg, the service" should {
    "respond with the ConfigSetting if found" in new Scope {
      val expectedConfigSettings = Seq(configSetting)

      // Mock Repository call
      val expectedQueries = Seq(termQuery("service_name", serviceName))
      (mockRepository.search _)
        .expects(expectedQueries, "setting_name", SortOrder.ASC, ElasticSearchRepository.LimitMaxValue)
        .returning(Future.successful(expectedConfigSettings))

      service ! RetrieveConfigSettings(serviceName)

      // Verify service response
      expectMsg(expectedConfigSettings)
    }
    "respond with an empty Seq if no ConfigSettings are found" in new Scope {
      val expectedConfigSettings = Seq.empty

      // Mock Repository call
      val expectedQueries = Seq(termQuery("service_name", serviceName))
      (mockRepository.search _)
        .expects(expectedQueries, "setting_name", SortOrder.ASC, ElasticSearchRepository.LimitMaxValue)
        .returning(Future.successful(expectedConfigSettings))

      service ! RetrieveConfigSettings(serviceName)

      // Verify service response
      expectMsg(Seq.empty)
    }
    "respond with Failure if the call to ElasticSearch fails" in new Scope {
      val elasticException = new RuntimeException("BOOM!")

      // Mock Repository call
      val expectedQueries = Seq(termQuery("service_name", serviceName))
      (mockRepository.search _)
        .expects(expectedQueries, "setting_name", SortOrder.ASC, ElasticSearchRepository.LimitMaxValue)
        .returning(Future.failed(elasticException))

      service ! RetrieveConfigSettings(serviceName)

      // Verify service response
      expectMsgFailure(elasticException)
    }
  }

}

package argo.service.config

import akka.actor.Props
import akka.pattern.pipe
import argo.common.elastic.ElasticSearchRepository
import argo.common.service.{ NamedActor, Service }
import argo.domain.service.config._
import com.sksamuel.elastic4s.http.ElasticDsl.termQuery
import com.sksamuel.elastic4s.searches.sort.SortOrder

import scala.concurrent.Future
import scala.util.{ Failure, Success }

object ConfigSettingQueryService extends NamedActor {
  override val Name = "config-setting-query-service"

  def props(): Props = {
    Props(new ConfigSettingQueryService())
  }

}

class ConfigSettingQueryService extends Service {

  log.debug("ConfigSettingQueryService is starting at {}", self.path)

  implicit val ec = context.dispatcher
  val repository = ConfigSettingRepository(context.system)

  def receive: Receive = {

    case RetrieveConfigSetting(serviceName, settingName) => {

      def search(): Future[Option[ConfigSetting]] = {
        val queries = Seq(termQuery("service_name", serviceName), termQuery("setting_name", settingName))
        repository.search(queries, "setting_name", SortOrder.ASC, 1).map(_.headOption)
      }

      log.info("[ ConfigSetting: [{}/{}] ] - [Retrieve ConfigSetting] - Retrieving Config Setting from ElasticSearch.", serviceName, settingName)

      val result = search()

      result andThen {
        case Success(_) => log.info("[ ConfigSetting: [{}/{}] ] - [Retrieve ConfigSetting] - Config Setting retrieval succeeded", serviceName, settingName)
        case Failure(e) => log.error("[ ConfigSetting: [{}/{}] ] - [Retrieve ConfigSetting] - Failed to retrieve Config Setting with error [{}]", serviceName, settingName, e)
      }

      result pipeTo sender
    }

    case RetrieveConfigSettings(serviceName) => {

      def search(): Future[Seq[ConfigSetting]] = {
        val queries = Seq(termQuery("service_name", serviceName))
        repository.search(queries, "setting_name", SortOrder.ASC, ElasticSearchRepository.LimitMaxValue)
      }

      log.info("[Retrieve ConfigSettings] - Retrieving Config Settings from ElasticSearch for service [{}].", serviceName)

      val result = search()

      result andThen {
        case Success(_) => log.info("[Retrieve ConfigSetting] - Config Setting retrieval succeeded for [{}]", serviceName)
        case Failure(e) => log.error("[Retrieve ConfigSetting] - Failed to retrieve Config Setting for [{}] with error [{}]", serviceName, e)
      }

      result pipeTo sender
    }

  }

}

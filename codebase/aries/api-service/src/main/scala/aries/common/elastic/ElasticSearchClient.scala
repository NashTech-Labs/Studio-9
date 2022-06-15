package aries.common.elastic

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import com.sksamuel.elastic4s.ElasticsearchClientUri
import com.sksamuel.elastic4s.http.HttpClient
import com.typesafe.config.Config

trait ElasticSearchClient extends Extension {

  val httpClient: HttpClient

}

object ElasticSearchClient extends ExtensionId[ElasticSearchClient] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): ElasticSearchClient = new ElasticSearchClient {
    override val httpClient = {
      val config = ElasticSearchSettings(system)

      val user = config.user
      val password = config.password
      val host = config.host
      val port = config.port
      val useSSL = config.useSSL
      val timeout = config.timeout

      HttpClient(ElasticsearchClientUri(s"elasticsearch://$host:$port?ssl=$useSSL"))

      /*
      // @Anthony: why do we have this commented?
      val elasticClient = {
      // TODO: refactor this, too many nested if-else
        val requestConfig: RequestConfig = RequestConfig.custom()
          .setConnectTimeout(timeout * 1000)
          .setSocketTimeout(timeout * 1000)
          .build()
        if (config.elasticConfig.useAuth) {
          val credentialsProvider = new BasicCredentialsProvider()
          credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(user, password))
          val restClient: RestClient = {
            val restClientBuilder = {
              if (config.elasticConfig.useSSL) {
                RestClient.builder(new HttpHost(host, port, "https"))
              } else {
                RestClient.builder(new HttpHost(host, port))
              }
            }
            restClientBuilder.setHttpClientConfigCallback(new RestClientBuilder.HttpClientConfigCallback() {
              override def customizeHttpClient(httpClientBuilder: HttpAsyncClientBuilder): HttpAsyncClientBuilder = {
                httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider).setDefaultRequestConfig(requestConfig)
              }
            }).setMaxRetryTimeoutMillis(timeout * 1000).build()
          }
          HttpClient.fromRestClient(restClient)
        } else {
          val restClient: RestClient = {
            val restClientBuilder = {
              if (config.elasticConfig.useSSL) {
                RestClient.builder(new HttpHost(host, port, "https"))
              } else {
                RestClient.builder(new HttpHost(host, port))
              }
            }
            restClientBuilder.setHttpClientConfigCallback(new RestClientBuilder.HttpClientConfigCallback() {
              override def customizeHttpClient(httpClientBuilder: HttpAsyncClientBuilder): HttpAsyncClientBuilder = {
                httpClientBuilder.setDefaultRequestConfig(requestConfig)
              }
            }).setMaxRetryTimeoutMillis(timeout * 1000).build()
          }
          HttpClient.fromRestClient(restClient)
        }
      }
      elasticClient
      */

    }
  }

  override def lookup(): ExtensionId[_ <: Extension] = ElasticSearchClient
}

// Settings
class ElasticSearchSettings(config: Config) extends Extension {
  private val elasticConfig = config.getConfig("elastic")

  val user = elasticConfig.getString("user")
  val password = elasticConfig.getString("password")
  val host = elasticConfig.getString("host")
  val port = elasticConfig.getInt("port")
  val job_index = elasticConfig.getString("job_index")
  val job_type = elasticConfig.getString("job_type")
  val heartbeat_index = elasticConfig.getString("heartbeat_index")
  val heartbeat_type = elasticConfig.getString("heartbeat_type")
  val useAuth = elasticConfig.getBoolean("useAuth")
  val useSSL = elasticConfig.getBoolean("useSSL")
  val timeout = elasticConfig.getInt("timeout")

}

object ElasticSearchSettings extends ExtensionId[ElasticSearchSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): ElasticSearchSettings = new ElasticSearchSettings(system.settings.config)

  override def lookup(): ExtensionId[_ <: Extension] = ElasticSearchSettings
}
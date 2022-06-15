package aries.testkit.service.elastic

import com.sksamuel.elastic4s.IndexAndType
import com.sksamuel.elastic4s.http.HttpClient
import com.sksamuel.elastic4s.testkit.{ DiscoveryLocalNodeProvider, ElasticMatchers, HttpElasticSugar, LocalNodeProvider }
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient
import org.scalatest.Suite

trait EsCluster extends ElasticMatchers with DiscoveryLocalNodeProvider {

  val esIndex: String = "test"
  val esType: String = "docs"

}

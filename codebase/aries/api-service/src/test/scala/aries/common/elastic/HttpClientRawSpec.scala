package aries.common.elastic

import aries.common.json4s.AriesJson4sSupport
import aries.testkit.service.elastic.EsCluster
import com.sksamuel.elastic4s.IndexAndType
import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.testkit.DiscoveryLocalNodeProvider
import org.scalatest.WordSpecLike

// TODO: this test requires to have ElasticSearch running locally?
// If so, use DockerTestKit instead and move it to IT tests.
class HttpClientRawSpec extends WordSpecLike with AriesJson4sSupport with EsCluster with DiscoveryLocalNodeProvider {

  def indexAndType: IndexAndType = IndexAndType(esIndex, esType)

  "After indexing into an index, the index" should {
    "exist" in {
      http.execute {
        indexInto(indexAndType) fields "name" -> "John Smith"
      }.await
      indexExists(esIndex).index shouldBe (esIndex)
      http.execute {
        indexExists(esIndex)
      }.await.isExists shouldBe true
    }
  }

  "Deleting the index" should {
    "result in index not existing" in {
      http.execute {
        deleteIndex(esIndex)
      }.await
      http.execute {
        indexExists(esIndex)
      }.await.isExists shouldBe false
    }
  }
}


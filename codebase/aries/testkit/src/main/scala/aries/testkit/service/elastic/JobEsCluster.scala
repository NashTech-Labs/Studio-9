package aries.testkit.service.elastic

import aries.testkit.resourceAsLines
import aries.testkit.service.ServiceBaseSpec
import com.sksamuel.elastic4s.IndexAndType
import com.sksamuel.elastic4s.testkit.LocalNodeProvider
import org.scalatest.Suite
import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.http.HttpClient
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient

/**
 * Created by anthony.difrancesco on 8/22/17.
 */
trait JobEsCluster extends ServiceBaseSpec with EsCluster {

  val jobIndex: String = "cortex_jobs_dev"
  val heartbeatIndex: String = "cortex_job_heartbeats_dev"
  val jobIndexType: String = "job"
  val heartbeatIndexType: String = "heartbeat"
  def jobIndexAndType: IndexAndType = IndexAndType(jobIndex, jobIndexType)
  def heartbeatIndexAndType: IndexAndType = IndexAndType(heartbeatIndex, heartbeatIndexType)

  val jobMapping = resourceAsLines("/mappings/cortex-jobs-dev.json")
  http.execute {
    createIndex(jobIndex) source (jobMapping.mkString(""))
  }.await

  val heartbeatMapping = resourceAsLines("/mappings/cortex-job-heartbeats-dev.json")
  http.execute {
    createIndex(heartbeatIndex) source (heartbeatMapping.mkString(""))
  }.await

}

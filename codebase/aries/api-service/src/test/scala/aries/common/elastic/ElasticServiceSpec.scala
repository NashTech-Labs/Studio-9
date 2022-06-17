package aries.common.elastic

// TODO: leaving this as reference for writing integration tests for the ElasticSearchRepository.
// Consider using DockerTestKit for doing so.

//import aries.common.json4s.AriesJson4sSupport
//import aries.domain.service.heartbeat.{ CreateHeartbeat, HeartbeatEntity }
//import aries.domain.service.job._
//import aries.testkit.service.elastic.JobEsCluster
//import com.sksamuel.elastic4s.http.ElasticDsl._
//import org.scalatest.WordSpecLike

//class ElasticServiceSpec extends WordSpecLike with AriesJson4sSupport with JobEsCluster {
//
//  val UUID1 = java.util.UUID.randomUUID()
//  val UUID2 = java.util.UUID.randomUUID()
//  val UUID3 = java.util.UUID.randomUUID()
//  val UUID4 = java.util.UUID.randomUUID()
//
//  trait JobScope extends ElasticService {
//
//    override val index = jobIndex
//    override val indexType = jobIndexType
//    override val elasticClient = http
//
//    val jobId = mockRandomUUID
//    val date = mockCurrentDate
//    val ownerId = mockRandomUUID
//    val jobType = JobType.Train
//    val jobStatus = JobStatus.Submitted
//    val inputPath = "inputPath"
//    val outputPath = "ouputPath"
//
//    val jobRequest = CreateJobData(
//      id           = jobId,
//      created_at   = mockCurrentDate,
//      owner        = ownerId,
//      job_type     = jobType,
//      status       = jobStatus,
//      input_path   = inputPath,
//      started_at   = Some(mockCurrentDate),
//      completed_at = Some(mockCurrentDate),
//      output_path  = Some(outputPath)
//    )
//    val jobResponse = JobEntity(
//      id           = jobId,
//      created_at   = mockCurrentDate,
//      owner        = ownerId,
//      job_type     = jobType,
//      status       = jobStatus,
//      input_path   = inputPath,
//      started_at   = Some(mockCurrentDate),
//      completed_at = Some(mockCurrentDate),
//      output_path  = Some(outputPath)
//    )
//    val ownerId2 = UUID1
//    val jobStatus2 = JobStatus.Running
//    val jobType2 = JobType.Predict
//    val jobId2 = UUID2
//    val jobId3 = UUID3
//    val jobId4 = UUID4
//    val job1 = jobRequest
//    val job2 = jobRequest.copy(id = jobId2, owner = ownerId2)
//    val job3 = jobRequest.copy(id = jobId3, status = jobStatus2)
//    val job4 = job2.copy(id = jobId4, job_type = jobType2)
//    val jobResp1 = jobResponse
//    val jobResp2 = jobResponse.copy(id = jobId2, owner = ownerId2)
//    val jobResp3 = jobResponse.copy(id = jobId3, status = jobStatus2)
//    val jobResp4 = jobResp2.copy(id = jobId4, job_type = jobType2)
//    val jobList = List(job1, job2, job3, job4)
//  }
//
//  "doIndexInto" should {
//    "return the correct jobEntity when created successfully" in new JobScope {
//      val result = doIndexInto(jobRequest).await
//      result.contains(jobResponse) shouldBe true
//    }
//  }
//
//  "getById" should {
//    "return the correct jobEntity when successful" in new JobScope {
//      val result = getById(jobId).await
//      result.contains(jobResponse) shouldBe true
//    }
//    "return None when entity not found" in new JobScope {
//      val badJobId = java.util.UUID.randomUUID()
//      val result = getById(badJobId).await
//      result.isEmpty shouldBe true
//    }
//  }
//
//  "doUpdate" should {
//    "return the updated jobEntity when successful" in new JobScope {
//      val updateRequest = UpdateJobData(status = Some(JobStatus.Running))
//      val updatedJob = jobResponse.copy(status = JobStatus.Running)
//      val result = doUpdate(jobId, updateRequest).await
//      result.contains(updatedJob) shouldBe true
//    }
//    "return None when entity not found" in new JobScope {
//      val badJobId = java.util.UUID.randomUUID()
//      val updateRequest = UpdateJobData(status = Some(JobStatus.Running))
//      val result = doUpdate(jobId, updateRequest).await
//      result.isEmpty shouldBe true
//    }
//  }
//
//  "doDeleteById" should {
//    "return the updated jobEntity with status cancelled when successful" in new JobScope {
//      val cancelledJob = jobResponse.copy(status = JobStatus.Cancelled)
//      val result = doDeleteById(jobId).await
//      result.contains(cancelledJob) shouldBe true
//    }
//    "return None when entity not found" in new JobScope {
//      val badJobId = java.util.UUID.randomUUID()
//      val cancelledJob = jobResponse.copy(status = JobStatus.Cancelled)
//      val result = doDeleteById(jobId).await
//      result.isEmpty shouldBe true
//    }
//  }
//
//  "doSearch" should {
//    "return the correct set of jobEntities for searching by owner" in new JobScope {
//      jobList.foreach(doIndexInto)
//      val jobSearchByOwner1 = JobSearchCriteria(owner = Some(ownerId))
//      val jobSearchByOwner2 = JobSearchCriteria(owner = Some(ownerId2))
//      val jsboResult1 = doSearch(jobSearchByOwner1).await
//      val jsboResult2 = doSearch(jobSearchByOwner2).await
//      assert(jsboResult1.contains(jobResp1) && jsboResult1.contains(jobResp3) && jsboResult1.length == 2)
//      assert(jsboResult2.contains(jobResp2) && jsboResult2.contains(jobResp4) && jsboResult2.length == 2)
//    }
//    "return the correct set of jobEntities for searching by status" in new JobScope {
//      val jobSearchByStatus1 = JobSearchCriteria(status = Some(jobStatus))
//      val jobSearchByStatus2 = JobSearchCriteria(status = Some(jobStatus2))
//      val jsbsResult1 = doSearch(jobSearchByStatus1).await
//      val jsbsResult2 = doSearch(jobSearchByStatus2).await
//      assert(jsbsResult1.contains(jobResp1) && jsbsResult1.contains(jobResp2) && jsbsResult1.contains(jobResp4) && jsbsResult1.length == 3)
//      assert(jsbsResult2.contains(jobResp3) && jsbsResult2.length == 1)
//    }
//    "return the correct set of jobEntities for searching by job_type" in new JobScope {
//      val jobSearchByType1 = JobSearchCriteria(job_type = Some(jobType))
//      val jobSearchByType2 = JobSearchCriteria(job_type = Some(jobType2))
//      val jsbtResult1 = doSearch(jobSearchByType1).await
//      val jsbtResult2 = doSearch(jobSearchByType2).await
//      assert(jsbtResult1.contains(jobResp1) && jsbtResult1.contains(jobResp2) && jsbtResult1.contains(jobResp3) && jsbtResult1.length == 3)
//      assert(jsbtResult2.contains(jobResp4) && jsbtResult2.length == 1)
//    }
//    "return the correct set of jobEntities for searching by a combination of parameters" in new JobScope {
//      val jobSearch1 = JobSearchCriteria(owner = Some(ownerId), status = Some(jobStatus), job_type = Some(jobType))
//      val jobSearch2 = JobSearchCriteria(owner = Some(ownerId2), job_type = Some(jobType))
//      val jobSearch3 = JobSearchCriteria(owner = Some(ownerId), status = Some(jobStatus2))
//      val jobSearch4 = JobSearchCriteria(status = Some(jobStatus), job_type = Some(jobType2))
//      val jsResult1 = doSearch(jobSearch1).await
//      val jsResult2 = doSearch(jobSearch2).await
//      val jsResult3 = doSearch(jobSearch3).await
//      val jsResult4 = doSearch(jobSearch4).await
//      assert(jsResult1.contains(jobResp1) && jsResult1.length == 1)
//      assert(jsResult2.contains(jobResp2) && jsResult2.length == 1)
//      assert(jsResult3.contains(jobResp3) && jsResult3.length == 1)
//      assert(jsResult4.contains(jobResp4) && jsResult4.length == 1)
//    }
//    "return an empty Seq when no jobs are found for specified criteria" in new JobScope {
//      val jobSearch = JobSearchCriteria(owner = Some(java.util.UUID.randomUUID()))
//      val result = doSearch(jobSearch).await
//      result.isEmpty shouldBe true
//    }
//  }
//
//  "listAll" in new JobScope {
//    val jobResults = Seq(jobResp1, jobResp2, jobResp3, jobResp4)
//    val result = listAll().await
//    assert(result.contains(jobResp1) && result.contains(jobResp2) && result.contains(jobResp3) && result.contains(jobResp4) && result.length == 4)
//  }
//
//  trait HeartbeatScope extends ElasticService {
//
//    override val index = heartbeatIndex
//    override val indexType = heartbeatIndexType
//    override val elasticClient = http
//
//    val jobId = mockRandomUUID
//    val date = mockCurrentDate
//    val id = UUID1
//
//    val heartbeatRequest = CreateHeartbeat(
//      id         = id,
//      job_id     = jobId,
//      created_at = mockCurrentDate
//    )
//    val heartbeatResponse = HeartbeatEntity(
//      id         = id,
//      job_id     = jobId,
//      created_at = mockCurrentDate
//    )
//  }
//
//  "doHeartbeatIndexInto" should {
//    "return the correct heartbeatEntity when created successfully" in new HeartbeatScope {
//      val result = doHeartbeatIndexInto(heartbeatRequest).await
//      result.contains(heartbeatResponse) shouldBe true
//    }
//  }
//
//  "doHeartbeatGetById" should {
//    "return the correct heartbeatEntity when successful" in new HeartbeatScope {
//      val result = doHeartbeatIndexInto(heartbeatRequest).await
//      result.contains(heartbeatResponse) shouldBe true
//    }
//    "return None when entity not found" in new HeartbeatScope {
//      val badJobId = java.util.UUID.randomUUID()
//      val result = getHeartbeatById(badJobId).await
//      result.isEmpty shouldBe true
//    }
//  }
//
//}
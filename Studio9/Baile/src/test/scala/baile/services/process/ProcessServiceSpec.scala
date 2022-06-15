package baile.services.process

import java.time.Instant
import java.util.UUID

import akka.testkit.TestProbe
import baile.BaseSpec
import baile.dao.process.ProcessDao
import baile.dao.process.ProcessDao.HandlerClassNameIn
import baile.daocommons.filters.{ Filter, TrueFilter }
import baile.daocommons.sorting.Direction.Ascending
import baile.daocommons.sorting.SortBy
import baile.domain.usermanagement.RegularUser
import baile.services.process.ProcessMonitor._
import baile.services.process.ProcessService.{ ActionForbiddenError, ProcessNotFoundError, SortingFieldUnknown }
import baile.services.process.util.TestData._
import baile.services.usermanagement.util.TestData.SampleUser
import cats.implicits._
import org.mockito.ArgumentMatchers.{ any, anyInt, eq => eqTo }
import org.mockito.Mockito._

import scala.concurrent.ExecutionContext

class ProcessServiceSpec extends BaseSpec {

  private val processMonitor = TestProbe()
  private val processDao = mock[ProcessDao]
  private val processService = new ProcessService(processMonitor.ref, processDao)
  private val dateTime = Instant.now()

  implicit val user: RegularUser = SampleUser

  "ProcessService#startProcess" should {

    "start new process" in {
      whenReady {
        val result = processService.startProcess(
          JobId,
          TargetId,
          TargetType,
          classOf[SampleJobResultHandler],
          JobMeta,
          SampleUser.id,
          Some("token"),
        )
        processMonitor.expectMsg(StartProcess(
          JobId,
          user.id,
          Some("token"),
          TargetId,
          TargetType,
          classOf[SampleJobResultHandler],
          SerializedJobMeta
        ))
        processMonitor.reply(SampleProcess)
        result
      } (_ shouldBe SampleProcess)
    }

  }

  "ProcessService#getProcess(id)" should {

    "return process by id from monitor" in {
      whenReady {
        val result = processService.getProcess(SampleProcess.id)
        processMonitor.expectMsg(GetProcessById(SampleProcess.id))
        processMonitor.reply(Some(SampleProcess))
        result
      } (_ shouldBe SampleProcess.asRight)
    }

    "return process by id from dao" in
      whenReady {
        when(processDao.list(any[Filter], anyInt, anyInt, any[Option[SortBy]])(any[ExecutionContext]))
          .thenReturn(future(Seq(SampleProcess)))

        val result = processService.getProcess(SampleProcess.id)
        processMonitor.expectMsg(GetProcessById(SampleProcess.id))
        processMonitor.reply(None)
        result
      } (_ shouldBe SampleProcess.asRight)

    "return nothing" in
      whenReady {
        when(processDao.list(any[Filter], anyInt, anyInt, any[Option[SortBy]])(any[ExecutionContext]))
          .thenReturn(future(Seq.empty))

        val result = processService.getProcess(SampleProcess.id)
        processMonitor.expectMsg(GetProcessById(SampleProcess.id))
        processMonitor.reply(None)
        result
      } (_ shouldBe ProcessNotFoundError.asLeft)

  }

  "ProcessService#getProcess(target)" should {

    val process = SampleProcess.entity

    "return process by target from monitor" in {
      whenReady {
        val result = processService.getProcess(process.targetId, process.targetType, None)
        processMonitor.expectMsg(GetProcessByTargetAndHandler(process.targetId, process.targetType, None))
        processMonitor.reply(Some(SampleProcess))
        result
      } (_ shouldBe SampleProcess.asRight)
    }

    "return process by target from dao" in
      whenReady {
        when(processDao.list(any[Filter], anyInt, anyInt, any[Option[SortBy]])(any[ExecutionContext]))
          .thenReturn(future(Seq(SampleProcess)))

        val result = processService.getProcess(process.targetId, process.targetType, None)
        processMonitor.expectMsg(GetProcessByTargetAndHandler(process.targetId, process.targetType, None))
        processMonitor.reply(None)
        result
      } (_ shouldBe SampleProcess.asRight)

    "return nothing" in
      whenReady {
        when(processDao.list(any[Filter], anyInt, anyInt, any[Option[SortBy]])(any[ExecutionContext]))
          .thenReturn(future(Seq.empty))

        val result = processService.getProcess(process.targetId, process.targetType, None)
        processMonitor.expectMsg(GetProcessByTargetAndHandler(process.targetId, process.targetType, None))
        processMonitor.reply(None)
        result
      } (_ shouldBe ProcessNotFoundError.asLeft)

    "return process by target from monitor based on handler" in {
      whenReady {
        val result = processService.getProcess(
          process.targetId,
          process.targetType,
          Some(classOf[SampleJobResultHandler])
        )
        processMonitor.expectMsg(GetProcessByTargetAndHandler(
          process.targetId,
          process.targetType,
          Some(classOf[SampleJobResultHandler])
        ))
        processMonitor.reply(Some(SampleProcess))
        result
      } (_ shouldBe SampleProcess.asRight)
    }

  }

  "ProcessService#cancelProcess" should {

    "cancel process" in {
      whenReady {
        val result = processService.cancelProcess(SampleProcess.id)
        processMonitor.expectMsg(GetProcessById(SampleProcess.id))
        processMonitor.reply(Some(SampleProcess))
        processMonitor.expectMsg(CancelProcess(SampleProcess.id))
        processMonitor.reply(())
        result
      } (_ shouldBe ().asRight)
    }

    "return not found error when process is not found" in {
      whenReady {
        val result = processService.cancelProcess(SampleProcess.id)
        processMonitor.expectMsg(GetProcessById(SampleProcess.id))
        processMonitor.reply(None)
        result
      } (_ shouldBe ProcessNotFoundError.asLeft)
    }

    "return forbidden error when process does not belong to the caller" in {
      whenReady {
        val result = processService.cancelProcess(SampleProcess.id)(user.copy(id = UUID.randomUUID))
        processMonitor.expectMsg(GetProcessById(SampleProcess.id))
        processMonitor.reply(Some(SampleProcess))
        result
      } (_ shouldBe ActionForbiddenError.asLeft)
    }

  }


  "ProcessService#cancelProcesses" should {

    val process = SampleProcess.entity

    "cancel process" in {
      whenReady {
        val result = processService.cancelProcesses(process.targetId, process.targetType)
        processMonitor.expectMsg(GetProcessesByTargetAndHandler(process.targetId, process.targetType, None))
        processMonitor.reply(Seq(SampleProcess))
        processMonitor.expectMsg(CancelProcess(SampleProcess.id))
        processMonitor.reply(())
        result
      } (_ shouldBe ().asRight)
    }

    "return forbidden error when process does not belong to the caller" in {
      whenReady {
        val result = processService.cancelProcesses(
          process.targetId,
          process.targetType
        )(user.copy(id = UUID.randomUUID))
        processMonitor.expectMsg(GetProcessesByTargetAndHandler(process.targetId, process.targetType, None))
        processMonitor.reply(Seq(SampleProcess))
        result
      } (_ shouldBe ActionForbiddenError.asLeft)
    }

  }

  "ProcessService#list" should {

    "get all processes" in {
      when(processDao.list(
        filterContains(TrueFilter),
        anyInt,
        anyInt,
        any[Option[SortBy]]
      )(any[ExecutionContext])).thenReturn(future(Seq(SampleProcess)))
      when(processDao.count(any[Filter])(any[ExecutionContext])).thenReturn(future(1))
      whenReady(processService.list(
        Nil, 1, 10, None, Option(Seq(dateTime, dateTime)),Option(Seq(dateTime, dateTime)))) {
        _ shouldBe Right((Seq(SampleProcess), 1))
      }
    }

    "get all processes for cv model train job" in {
      when(processDao.list(
        filterContains(HandlerClassNameIn(Seq("CVModelTrainResultHandler"))),
        anyInt,
        anyInt,
        any[Option[SortBy]]
      )(any[ExecutionContext])).thenReturn(future(Seq(SampleProcess)))
      when(processDao.count(any[Filter])(any[ExecutionContext])).thenReturn(future(1))
      whenReady(processService.list(
        Seq("created"),
        5,
        100,
        Some(Seq("CVModelTrainResultHandler")),
        Some(Seq(dateTime, dateTime)),
        Some(Seq(dateTime, dateTime))
      )) {
        _ shouldBe Right((Seq(SampleProcess), 1))
      }
    }

    "return all processes sorted by created, started and completed" in {
      when(processDao.list(
        any[Filter],
        anyInt,
        anyInt,
        eqTo(Some(SortBy(
          (ProcessDao.Created, Ascending),
          (ProcessDao.Started, Ascending),
          (ProcessDao.Completed, Ascending)
        )))
      )(any[ExecutionContext])).thenReturn(future(Seq(SampleProcess)))
      when(processDao.count(any[Filter])(any[ExecutionContext])).thenReturn(future(1))
      whenReady(processService.list(
        Seq("created", "started", "completed"),
        5,
        100,
        None,
        None,
        None
      )) {
        _ shouldBe Right((Seq(SampleProcess), 1))
      }
    }

    "return error when sorting field is unknown" in {
      whenReady(processService.list(
        Seq("Updated"),
        5,
        100,
        None,
        Some(Seq(dateTime, dateTime)),
        Some(Seq(dateTime, dateTime))
      )) {
        _ shouldBe Left(SortingFieldUnknown)
      }
    }

  }

}

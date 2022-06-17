package baile.services.onlinejob

import baile.BaseSpec
import baile.dao.asset.Filters.NameIs
import baile.dao.onlinejob.OnlineJobDao
import baile.daocommons.filters.TrueFilter
import baile.domain.onlinejob.OnlineJob
import baile.domain.usermanagement.User
import baile.services.asset.sharing.AssetSharingService
import baile.services.onlinejob.util.TestData._
import baile.services.usermanagement.util.TestData.SampleUser
import baile.services.onlinejob.OnlineJobService.OnlineJobServiceError
import baile.services.onlinejob.OnlinePredictionConfigurator.OnlinePredictionConfiguratorError
import baile.services.project.ProjectService
import cats.implicits._
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito.when

import scala.concurrent.ExecutionContext

class OnlineJobServiceSpec extends BaseSpec {

  val mockedOnlineJobDao: OnlineJobDao = mock[OnlineJobDao]
  val mockedOnlinePredictionConfigurator: OnlinePredictionConfigurator = mock[OnlinePredictionConfigurator]
  val assetSharingService: AssetSharingService = mock[AssetSharingService]
  val projectService: ProjectService = mock[ProjectService]

  val service = new OnlineJobService(
    mockedOnlineJobDao,
    mockedOnlinePredictionConfigurator,
    assetSharingService,
    projectService
  )

  implicit val user = SampleUser

  "OnlineJobService#create" should {
    "Create onlineJob successfully" in {
      when(mockedOnlineJobDao.count(filterContains(NameIs("name")))(any[ExecutionContext])) thenReturn future(0)
      when(mockedOnlineJobDao.count(TrueFilter)) thenReturn future(0)
      when(mockedOnlinePredictionConfigurator.configure(
        any[OnlinePredictionCreateOptions]
      )(any[User]))thenReturn future(OnlinePredictionOptionsSample.asRight)
      when(mockedOnlineJobDao.create(any[OnlineJob])(any[ExecutionContext])) thenReturn future("onlineJobId")

      whenReady(service.create(Some("name"), true, OnlinePredictionCreateOptionsSample, None)) { result =>
        assert(result.isRight)
        assert(result.right.get.id == "onlineJobId")
        assert(result.right.get.entity.status == OnlineJobSample.status)
        assert(result.right.get.entity.options == OnlineJobSample.options)
        assert(result.right.get.entity.enabled == OnlineJobSample.enabled)
      }
    }

    "Not Create onlineJob when online configurator return any error " in {
      when(mockedOnlineJobDao.count(filterContains(NameIs("name")))(any[ExecutionContext])) thenReturn future(0)
      when(mockedOnlineJobDao.count(TrueFilter)) thenReturn future(0)
      when(mockedOnlinePredictionConfigurator.configure(
        any[OnlinePredictionCreateOptions]
      )(any[User]))thenReturn future(OnlinePredictionConfiguratorError.AccessDenied.asLeft)

      whenReady(service.create(Some("name"), true, OnlinePredictionCreateOptionsSample, None)) { result =>
        assert(result.isLeft)
        assert(result.left.get == OnlineJobServiceError.AccessDenied)
      }
    }

    "Not Create onlineJob when online configurator return any error that model not found " in {
      when(mockedOnlineJobDao.count(filterContains(NameIs("name")))(any[ExecutionContext])) thenReturn future(0)
      when(mockedOnlineJobDao.count(TrueFilter)) thenReturn future(0)
      when(mockedOnlinePredictionConfigurator.configure(
        any[OnlinePredictionCreateOptions]
      )(any[User]))thenReturn future(OnlinePredictionConfiguratorError.ModelNotFound.asLeft)

      whenReady(service.create(Some("name"), true, OnlinePredictionCreateOptionsSample, None)) { result =>
        assert(result.isLeft)
        assert(result.left.get == OnlineJobServiceError.ModelNotFound)
      }
    }

    "Not Create onlineJob when online configurator return any error that model not active " in {
      when(mockedOnlineJobDao.count(filterContains(NameIs("name")))(any[ExecutionContext])) thenReturn future(0)
      when(mockedOnlineJobDao.count(TrueFilter)) thenReturn future(0)
      when(mockedOnlinePredictionConfigurator.configure(
        any[OnlinePredictionCreateOptions]
      )(any[User]))thenReturn future(OnlinePredictionConfiguratorError.ModelNotActive.asLeft)

      whenReady(service.create(Some("name"), true, OnlinePredictionCreateOptionsSample, None)) { result =>
        assert(result.isLeft)
        assert(result.left.get == OnlineJobServiceError.ModelNotActive)
      }
    }

    "Not Create onlineJob when online configurator return any error that invalid model type " in {
      when(mockedOnlineJobDao.count(filterContains(NameIs("name")))(any[ExecutionContext])) thenReturn future(0)
      when(mockedOnlineJobDao.count(TrueFilter)) thenReturn future(0)
      when(mockedOnlinePredictionConfigurator.configure(
        any[OnlinePredictionCreateOptions]
      )(any[User]))thenReturn future(OnlinePredictionConfiguratorError.InvalidModelType.asLeft)

      whenReady(service.create(Some("name"), true, OnlinePredictionCreateOptionsSample, None)) { result =>
        assert(result.isLeft)
        assert(result.left.get == OnlineJobServiceError.InvalidModelType)
      }
    }

    "Not Create onlineJob when onlineJob already exist" in {
      when(mockedOnlineJobDao.count(filterContains(NameIs("name")))(any[ExecutionContext])) thenReturn future(0)
      when(mockedOnlineJobDao.count(TrueFilter)) thenReturn future(1)
      whenReady(service.create(Some("name"), true, OnlinePredictionCreateOptionsSample, None)) { result =>
        assert(result.isLeft)
        assert(result.left.get == OnlineJobServiceError.OnlineJobAlreadyExists)
      }
    }
  }

}

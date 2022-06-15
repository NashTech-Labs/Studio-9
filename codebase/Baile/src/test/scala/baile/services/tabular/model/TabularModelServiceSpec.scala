package baile.services.tabular.model

import java.time.Instant

import akka.stream.scaladsl.Source
import baile.BaseSpec
import baile.dao.asset.Filters.{ InLibraryIs, NameIs, OwnerIdIs }
import baile.dao.tabular.model.TabularModelDao
import baile.daocommons.WithId
import baile.daocommons.filters.{ Filter, IdIs }
import baile.domain.common.{ ClassReference, CortexModelReference, Version }
import baile.domain.dcproject.DCProjectPackage
import baile.domain.table._
import baile.domain.tabular.model.{ TabularModel, TabularModelStatus }
import baile.domain.usermanagement.User
import baile.services.asset.sharing.AssetSharingService
import baile.services.common.MLEntityExportImportService
import baile.services.cortex.job.CortexJobService
import baile.services.dcproject.DCProjectPackageService
import baile.services.process.ProcessService
import baile.services.project.ProjectService
import baile.services.remotestorage.RemoteStorageService
import baile.services.table.TableService
import baile.services.tabular.model.TabularModelService.TabularModelServiceError
import baile.services.tabular.model.TabularModelService.TabularModelServiceError._
import baile.services.tabular.model.util.export.TabularModelExportMeta
import baile.services.usermanagement.util.TestData.SampleUser
import cats.implicits._
import cortex.api.job.table.DataSource
import org.mockito.ArgumentMatchers.{ any, eq => eqTo }
import org.mockito.Mockito._

import scala.concurrent.ExecutionContext
import scala.util.Success

class TabularModelServiceSpec extends BaseSpec {

  private val dao = mock[TabularModelDao]
  private val processService = mock[ProcessService]
  private val cortexJobService = mock[CortexJobService]
  private val tableService = mock[TableService]
  private val tabularModelCommonService = mock[TabularModelCommonService]
  private val assetSharingService = mock[AssetSharingService]
  private val packageService = mock[DCProjectPackageService]
  private val projectService = mock[ProjectService]
  private val mlEntitiesStorage = mock[RemoteStorageService]
  private val mlEntityService = mock[MLEntityExportImportService]

  private val service = new TabularModelService(
    dao = dao,
    processService = processService,
    cortexJobService = cortexJobService,
    tableService = tableService,
    tabularModelCommonService = tabularModelCommonService,
    assetSharingService = assetSharingService,
    exportImportService = mlEntityService,
    projectService = projectService,
    packageService = packageService,
    mlEntitiesStorage = mlEntitiesStorage
  )

  implicit private val user: User = SampleUser

  private val dataSource = DataSource(None)

  object Updating {

    object Valid {

      val Model: WithId[TabularModel] = TabularModelRandomGenerator.randomModel(
        ownerId = user.id,
        status = TabularModelStatus.Predicting,
        description = Some("description")
      )
      when(dao.get(
        eqTo(Model.id)
      )(any[ExecutionContext])) thenReturn future(Some(Model))
      when(dao.count(
        eqTo(NameIs("model") && OwnerIdIs(user.id) && InLibraryIs(true) && !IdIs(Model.id))
      )(any[ExecutionContext])).thenReturn(future(0))
      when(dao.update(eqTo(Model.id), any[TabularModel => TabularModel].apply)(any[ExecutionContext]))
        .thenReturn(future(Some(Model)))
    }

    object Invalid {

      val Model: WithId[TabularModel] = TabularModelRandomGenerator.randomModel(
        ownerId = user.id,
        status = TabularModelStatus.Predicting
      )
      when(dao.count(eqTo(NameIs("xyz") && OwnerIdIs(user.id) && InLibraryIs(true) && !IdIs(Model.id)))
      (any[ExecutionContext])).thenReturn(future(1))
      when(dao.get(
        eqTo(Model.id)
      )(any[ExecutionContext])) thenReturn future(Some(Model))
    }

  }

  object Export {
    val ModelPackage: WithId[DCProjectPackage] = WithId(DCProjectPackage(
      ownerId = Some(user.id),
      dcProjectId = None,
      name = randomString(),
      version = Some(Version(1, 0, 0, None)),
      location = None,
      created = Instant.now(),
      description = None,
      isPublished = true
    ), randomString())

    val Model: WithId[TabularModel] = TabularModelRandomGenerator.randomModel(
      ownerId = user.id,
      status = TabularModelStatus.Active,
      cortexModelReference = Some(CortexModelReference(randomString(), randomPath())),
      classReference = ClassReference(
        className = randomString(),
        moduleName = randomString(),
        packageId = ModelPackage.id
      )
    )
  }

  when(tableService.buildDataSource(any[Table])).thenReturn(Success(dataSource))


  "TabularModelService#save" should {

    "should save the model successfully" in {
      import Updating.Valid._

      whenReady(service.save(Model.id, "model", Model.entity.description)) { result =>
        result shouldBe Right(Model)
      }
    }

    "return error response when model name already exists" in {
      import Updating.Invalid._

      whenReady(service.save(Model.id, "xyz", None)) { result =>
        result shouldBe Left(TabularModelServiceError.NameIsTaken)
      }
    }

    "return error response when model name is empty" in {
      import Updating.Invalid._
      whenReady(service.save(Model.id, "", None)) { result =>
        result shouldBe Left(TabularModelServiceError.EmptyTabularModelName)
      }
    }
  }

  "TabularModelService#update" should {

    "return success response" in {
      import Updating.Valid._
      whenReady(service.update(Model.id, Some("model"), Some("description"))) { response =>
        response shouldBe Right(Model)
      }
    }

    import Updating.Invalid._
    "return error response when another user model with same name exists" in {
      whenReady(service.update(Model.id, Some("xyz"), None)) { response =>
        response shouldBe Left(ModelNameAlreadyExists)
      }
    }
    "return error response when given name is empty" in {
      whenReady(service.update(Model.id, Some(""), None)) { response =>
        response shouldBe Left(ModelNameIsEmpty)
      }

    }
  }

  "TabularModelService#clone" should {

    val model: WithId[TabularModel] = TabularModelRandomGenerator.randomModel(
      ownerId = user.id,
      status = TabularModelStatus.Active,
      cortexModelReference = Some(CortexModelReference(randomString(), randomString()))
    )

    "clone tabularModel without any errors" in {
      when(dao.get(any[String])(any[ExecutionContext])).thenReturn(future(Some(model)))
      when(dao.count(any[Filter])(any[ExecutionContext])).thenReturn(future(0))
      when(dao.create(any[String => TabularModel])(any[ExecutionContext])).thenReturn(future(model))
      whenReady(service.clone("modelId", "ClonedTabularModel", Some("cloned model"), None)) { result =>
        assert(result.isRight)
        assert(result.right.get == model)
      }
    }

    "not able to clone when tabularModel not found" in {
      when(dao.get(any[String])(any[ExecutionContext])).thenReturn(future(None))
      whenReady(service.clone("modelId", "ClonedTabularModel", Some("cloned model"), None)) { result =>
        result shouldBe TabularModelServiceError.ModelNotFound.asLeft
      }
    }

  }

  "TabularModelService#getStateFileUrl" should {

    "return success response" in {
      val model: WithId[TabularModel] = TabularModelRandomGenerator.randomModel(
        ownerId = user.id,
        status = TabularModelStatus.Active,
        cortexModelReference = Some(CortexModelReference(randomString(), randomString()))
      )
      val expectedUrl = model.entity.cortexModelReference.map(_.cortexFilePath).get

      when(dao.get(
        eqTo(model.id)
      )(any[ExecutionContext])) thenReturn future(Some(model))
      when(mlEntitiesStorage.getExternalUrl(expectedUrl)) thenReturn "http://foo"

      whenReady(service.getStateFileUrl(model.id)) { url =>
        url shouldBe Right("http://foo")
      }
    }

    "return error if model with given id not found" in {
      val modelId = randomString()
      when(dao.get(
        eqTo(modelId)
      )(any[ExecutionContext])) thenReturn future(None)

      whenReady(service.getStateFileUrl(modelId)) { result =>
        result shouldBe TabularModelServiceError.ModelNotFound.asLeft
      }
    }

    "return error when model is not Active" in {
      val model: WithId[TabularModel] = TabularModelRandomGenerator.randomModel(
        ownerId = user.id,
        status = TabularModelStatus.Error
      )

      when(dao.get(
        eqTo(model.id)
      )(any[ExecutionContext])) thenReturn future(Some(model))

      whenReady(service.getStateFileUrl(model.id)) { result =>
        result shouldBe TabularModelServiceError.ModelNotActive.asLeft
      }
    }

    "return error when model did not contain file path" in {
      val model: WithId[TabularModel] = TabularModelRandomGenerator.randomModel(
        ownerId = user.id,
        status = TabularModelStatus.Active
      )

      when(dao.get(
        eqTo(model.id)
      )(any[ExecutionContext])) thenReturn future(Some(model))

      whenReady(service.getStateFileUrl(model.id)) { result =>
        result shouldBe TabularModelServiceError.ModelFilePathNotFound.asLeft
      }
    }

  }

  "TabularModelService#export" should {
    "fetch data from DAO" in {
      import akka.util.ByteString
      import Export._

      when(packageService.loadPackageMandatory(Model.entity.classReference.packageId)) thenReturn future(ModelPackage)

      when(dao.get(
        eqTo(Model.id)
      )(any[ExecutionContext])) thenReturn future(Some(Model))

      when(
        mlEntityService.exportEntity(
          Model.entity.cortexModelReference.map(_.cortexFilePath).get,
          TabularModelExportMeta(Model.entity, ModelPackage)
        )
      ).thenReturn(future(
        Source.single(ByteString(randomString()))
      ))

      whenReady(service.export(Model.id)) { result =>
        result shouldBe a [Right[_, _]]
        whenReady(result.right.get.runFold(Seq.empty[ByteString])(_ :+ _))(_.size should be > 0)
      }
    }

    "return error when model has no cortex reference" in {
      import Export._

      when(dao.get(
        eqTo(Model.id)
      )(any[ExecutionContext])) thenReturn future(Some(Model.map(_.copy(
        cortexModelReference = None
      ))))

      whenReady(service.export(Model.id)) {
        _.shouldBe(TabularModelServiceError.CantExportTabularModel.asLeft)
      }
    }

    "return error when model is not active" in {
      import Export._

      when(dao.get(
        eqTo(Model.id)
      )(any[ExecutionContext])) thenReturn future(Some(Model.map(_.copy(
        status = TabularModelStatus.Saving
      ))))

      whenReady(service.export(Model.id)) {
        _.shouldBe(TabularModelServiceError.ModelNotActive.asLeft)
      }
    }
  }
}

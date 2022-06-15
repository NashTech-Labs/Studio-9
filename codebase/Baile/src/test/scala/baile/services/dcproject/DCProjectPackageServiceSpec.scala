package baile.services.dcproject

import java.time.Instant
import java.util.UUID

import baile.ExtendedBaseSpec
import baile.RandomGenerators._
import baile.dao.cv.model.tlprimitives.CVTLModelPrimitiveDao
import baile.dao.cv.model.{ CVModelDao, CVModelPipelineSerializer }
import baile.dao.dcproject.DCProjectPackageDao
import baile.dao.dcproject.DCProjectPackageDao.Created
import baile.dao.experiment.ExperimentDao
import baile.dao.pipeline.category.CategoryDao
import baile.dao.pipeline.category.CategoryDao.CategoryIdIs
import baile.dao.pipeline.{ GenericExperimentPipelineSerializer, PipelineDao, PipelineOperatorDao }
import baile.daocommons.WithId
import baile.daocommons.sorting.SortBy
import baile.domain.asset.AssetType
import baile.domain.common.Version
import baile.domain.cv.model.tlprimitives.{ CVTLModelPrimitive, CVTLModelPrimitiveType }
import baile.domain.dcproject.{ DCProject, DCProjectPackage, DCProjectPackageArtifact, DCProjectStatus }
import baile.domain.pipeline.PipelineOperator
import baile.domain.pipeline.category.Category
import baile.domain.usermanagement.{ RegularUser, User }
import baile.services.cortex.job.CortexJobService
import baile.services.cortex.job.SupportedCortexJobTypes.SupportedCortexJobType
import baile.services.dcproject.DCProjectPackageService.DCProjectPackageServiceError.{
  DCProjectPackageNotFound,
  SortingFieldUnknown
}
import baile.services.dcproject.DCProjectPackageService.{
  DCProjectPackageServiceCreateError,
  DCProjectPackageServiceError,
  ExtendedPackageResponse,
  PipelineOperatorPublishParams
}
import baile.services.dcproject.DCProjectRandomGenerator._
import baile.services.process.ProcessService
import baile.services.process.util.ProcessRandomGenerator.randomProcess
import baile.services.remotestorage.RemoteStorageService
import baile.services.usermanagement.util.TestData._
import cortex.api.job.project.`package`.ProjectPackageRequest
import org.scalatest.prop.TableDrivenPropertyChecks
import play.api.libs.json.OWrites

class DCProjectPackageServiceSpec extends ExtendedBaseSpec with TableDrivenPropertyChecks {

  trait Setup {

    val dao: DCProjectPackageDao = mock[DCProjectPackageDao]
    val cvTLModelPrimitiveDao: CVTLModelPrimitiveDao = mock[CVTLModelPrimitiveDao]
    val dcProjectService: DCProjectService = mock[DCProjectService]
    val cortexJobService: CortexJobService = mock[CortexJobService]
    val processService: ProcessService = mock[ProcessService]
    val packagesStorage: RemoteStorageService = mock[RemoteStorageService]
    val cvModelDao: CVModelDao = mock[CVModelDao]
    val pipelineOperatorDao: PipelineOperatorDao = mock[PipelineOperatorDao]
    val pipelineDao: PipelineDao = mock[PipelineDao]
    val experimentDao: ExperimentDao = mock[ExperimentDao]
    val categoryDao: CategoryDao = mock[CategoryDao]
    val packagesStorageKeyPrefix: String = randomPath()
    val service: DCProjectPackageService = new DCProjectPackageService(
      dao,
      cvTLModelPrimitiveDao,
      cvModelDao,
      pipelineOperatorDao,
      experimentDao,
      pipelineDao,
      dcProjectService,
      cortexJobService,
      processService,
      packagesStorage,
      packagesStorageKeyPrefix,
      categoryDao
    )
    implicit val user: RegularUser = SampleUser
    val packageName: String = randomString().toLowerCase
    val globalPackageName: String = randomString().toLowerCase
    val dateTime: Instant = Instant.now()
    val version: Version = Version(1, 0, 0, None)
    val cortexJobId: UUID = UUID.randomUUID()
    val packageFile: String = s"$packageName-$version-py3-none-any.whl"
    val packageLocation: String = s"${randomPath()}/$packageFile"
    val dcProjectPackage = WithId(
      DCProjectPackage(
        ownerId = Some(user.id),
        dcProjectId = Some(randomString()),
        name = packageName,
        version = Some(version),
        location = Some(packageLocation),
        created = dateTime,
        description = None,
        isPublished = false
      ),
      randomString()
    )
    val category = WithId(
      Category(
        id = randomString(),
        name = randomString(),
        icon = randomString()
      ),
      randomString()
    )
    val updatedDCProjectpackage = dcProjectPackage.copy(
      entity = dcProjectPackage.entity.copy(name = "abc", description = Some("abc"))
    )

    val publishedPipelineOperator = WithId(
      PipelineOperator(
        name = "SCAE",
        description = None,
        category = Some("OTHER"),
        moduleName = "ml_lib.feature_extractors.backbones",
        className = "SCAE",
        packageId = dcProjectPackage.id,
        params = Seq(),
        inputs = Seq(),
        outputs = Seq()
      ),
      randomString()
    )
    val pipelineOperator: PipelineOperator = PipelineOperator(
      name = "SCAE",
      description = None,
      category = Some("OTHER"),
      moduleName = "ml_lib.feature_extractors.backbones",
      className = "SCAE",
      packageId = dcProjectPackage.id,
      params = Seq(),
      inputs = Seq(),
      outputs = Seq()
    )
    val packageV2Version = Version(2, 0, 0, None)
    val packageV2File: String = s"$packageName-$packageV2Version-py3-none-any.whl"
    val packageV2Location: String = s"${ randomPath() }/$packageV2File"
    val dcProjectPackageV2 = WithId(
      DCProjectPackage(
        ownerId = dcProjectPackage.entity.ownerId,
        dcProjectId = dcProjectPackage.entity.dcProjectId,
        name = packageName,
        version = Some(packageV2Version),
        location = Some(packageV2Location),
        created = dateTime,
        description = None,
        isPublished = true
      ),
      randomString()
    )
    val globalDcProjectPackage = WithId(
      DCProjectPackage(
        ownerId = None,
        dcProjectId = None,
        name = globalPackageName,
        version = Some(version),
        location = Some(randomPath()),
        created = dateTime,
        description = None,
        isPublished = true
      ),
      randomString()
    )
  }

  "DCProjectPackageService#create" should {

    "successfully publish package when no other package exists for the project" in new Setup {
      val dcProjectWithoutPackage: WithId[DCProject] = randomDCProject(
        status = DCProjectStatus.Idle,
        packageName = None
      )
      dcProjectService.get(dcProjectWithoutPackage.id)(*) shouldReturn future(Right(dcProjectWithoutPackage))
      dcProjectService.buildFullStoragePath(dcProjectWithoutPackage.entity, "") shouldReturn
        dcProjectWithoutPackage.entity.basePath
      dcProjectService.count(*) shouldReturn future(0)
      dao.count(*) shouldReturn future(0)
      dcProjectService.update(dcProjectWithoutPackage.id, *[DCProjectStatus], *) shouldReturn
        future(Some(dcProjectWithoutPackage))
      "prefix" willBe returned by packagesStorage.path(*, *)
      cortexJobService.submitJob(
        *[ProjectPackageRequest],
        user.id
      )(implicitly[SupportedCortexJobType[ProjectPackageRequest]]) shouldReturn future(cortexJobId)
      processService.startProcess(
        cortexJobId,
        dcProjectWithoutPackage.id,
        AssetType.DCProject,
        classOf[DCProjectBuildResultHandler],
        DCProjectBuildResultHandler.Meta(
          dcProjectId = dcProjectWithoutPackage.id,
          name = packageName,
          version = version,
          userId = user.id,
          packageAlreadyExists = false,
          description = None,
          analyzePipelineOperators = true
        ),
        user.id
      )(implicitly[OWrites[DCProjectBuildResultHandler.Meta]]) shouldReturn future(randomProcess())
      whenReady(service.create(
        dcProjectWithoutPackage.id,
        Some(packageName),
        version,
        None,
        None
      )(user)) {
        _ shouldBe Right(dcProjectWithoutPackage)
      }
    }

    "successfully publish package when package already exists for the project" in new Setup {
      val dcProjectWithPackage: WithId[DCProject] = randomDCProject(
        status = DCProjectStatus.Idle,
        packageName = Some(packageName),
        latestPackageVersion = Some(Version(1, 0, 0, None))
      )
      val secondVersion: Version = Version(1, 1, 0, None)
      dcProjectService.get(dcProjectWithPackage.id)(*) shouldReturn future(Right(dcProjectWithPackage))
      dcProjectService.buildFullStoragePath(dcProjectWithPackage.entity, "") shouldReturn
        dcProjectWithPackage.entity.basePath
      dcProjectService.update(dcProjectWithPackage.id, *[DCProjectStatus], *) shouldReturn
        future(Some(dcProjectWithPackage))
      "prefix" willBe returned by packagesStorage.path(*, *)
      cortexJobService.submitJob(
        *[ProjectPackageRequest],
        user.id
      )(implicitly[SupportedCortexJobType[ProjectPackageRequest]]) shouldReturn future(cortexJobId)
      processService.startProcess(
        cortexJobId,
        dcProjectWithPackage.id,
        AssetType.DCProject,
        classOf[DCProjectBuildResultHandler],
        DCProjectBuildResultHandler.Meta(
          dcProjectId = dcProjectWithPackage.id,
          name = packageName,
          version = secondVersion,
          userId = user.id,
          packageAlreadyExists = true,
          description = None,
          analyzePipelineOperators = true
        ),
        user.id
      )(implicitly[OWrites[DCProjectBuildResultHandler.Meta]]) shouldReturn future(randomProcess())
      whenReady(service.create(
        dcProjectWithPackage.id,
        None,
        secondVersion,
        None,
        None
      )(user)) {
        _ shouldBe Right(dcProjectWithPackage)
      }
    }

    "successfully publish package when provided package name is same as package name of project" in new Setup {
      val dcProjectWithPackage: WithId[DCProject] = randomDCProject(
        status = DCProjectStatus.Idle,
        packageName = Some(packageName),
        latestPackageVersion = Some(Version(1, 0, 0, None))
      )
      val secondVersion: Version = Version(1, 1, 0, None)
      dcProjectService.get(dcProjectWithPackage.id)(*) shouldReturn future(Right(dcProjectWithPackage))
      dcProjectService.buildFullStoragePath(dcProjectWithPackage.entity, "") shouldReturn
        dcProjectWithPackage.entity.basePath
      dcProjectService.update(dcProjectWithPackage.id, *[DCProjectStatus], *) shouldReturn
        future(Some(dcProjectWithPackage))
      "prefix" willBe returned by packagesStorage.path(*, *)
      cortexJobService.submitJob(
        *[ProjectPackageRequest],
        user.id
      )(implicitly[SupportedCortexJobType[ProjectPackageRequest]]) shouldReturn future(cortexJobId)
      processService.startProcess(
        cortexJobId,
        dcProjectWithPackage.id,
        AssetType.DCProject,
        classOf[DCProjectBuildResultHandler],
        DCProjectBuildResultHandler.Meta(
          dcProjectId = dcProjectWithPackage.id,
          name = packageName,
          version = secondVersion,
          userId = user.id,
          packageAlreadyExists = true,
          description = None,
          analyzePipelineOperators = true
        ),
        user.id
      )(implicitly[OWrites[DCProjectBuildResultHandler.Meta]]) shouldReturn future(randomProcess())
      whenReady(service.create(
        dcProjectWithPackage.id,
        Some(packageName),
        secondVersion,
        None,
        None
      )(user)) {
        _ shouldBe Right(dcProjectWithPackage)
      }
    }

    "fail when provided package name is different from package name already defined for the project" in new Setup {
      val dcProjectWithPackage: WithId[DCProject] = randomDCProject(
        status = DCProjectStatus.Idle,
        packageName = Some(packageName)
      )
      val newVersion: Version = Version(1, 1, 0, None)
      val providedPackageName: String = randomString()
      dcProjectService.get(dcProjectWithPackage.id)(*) shouldReturn future(Right(dcProjectWithPackage))
      whenReady(service.create(
        dcProjectWithPackage.id,
        Some(providedPackageName),
        newVersion,
        None,
        None
      )(user)) {
        _ shouldBe Left(DCProjectPackageServiceCreateError.PackageNameAlreadyDefined(packageName))
      }
    }

    "fail when version is not greater then latest package version " in new Setup {
      val dcProjectWithPackage: WithId[DCProject] = randomDCProject(
        status = DCProjectStatus.Idle,
        packageName = Some(packageName),
        latestPackageVersion = Some(Version(1, 0, 0, None))
      )
      val newVersion = Version(0, 1, 1, None)
      dcProjectService.get(dcProjectWithPackage.id)(*) shouldReturn future(Right(dcProjectWithPackage))
      whenReady(service.create(
        dcProjectWithPackage.id,
        None,
        newVersion,
        None,
        None
      )(user)) {
        _ shouldBe Left(DCProjectPackageServiceCreateError.VersionNotGreater)
      }
    }

    "fail when project is not idle " in new Setup {
      val publishDCProject: WithId[DCProject] = randomDCProject(
        status = DCProjectStatus.Building,
        packageName = Some(packageName)
      )
      val newVersion: Version = Version(1, 1, 0, None)
      dcProjectService.get(publishDCProject.id)(*) shouldReturn future(Right(publishDCProject))
      whenReady(service.create(
        publishDCProject.id,
        None,
        newVersion,
        None,
        None
      )(user)) {
        _ shouldBe Left(DCProjectPackageServiceCreateError.DCProjectNotIdle)
      }
    }

    "fail when package name is not provided when required" in new Setup {
      val dcProjectWithoutPackage: WithId[DCProject] = randomDCProject(
        status = DCProjectStatus.Idle,
        packageName = None
      )
      dcProjectService.get(dcProjectWithoutPackage.id)(*) shouldReturn future(Right(dcProjectWithoutPackage))
      whenReady(service.create(
        dcProjectWithoutPackage.id,
        None,
        version,
        None,
        None
      )(user)) {
        _ shouldBe Left(DCProjectPackageServiceCreateError.PackageNameIsRequired)
      }
    }

    "fail when provided package name is empty" in new Setup {
      val dcProject: WithId[DCProject] = randomDCProject(status = DCProjectStatus.Idle, packageName = None)
      val emptyPackageName: String = ""
      dcProjectService.get(dcProject.id)(*) shouldReturn future(Right(dcProject))
      whenReady(service.create(
        dcProject.id,
        Some(emptyPackageName),
        version,
        None,
        None
      )(user)) {
        _ shouldBe Left(DCProjectPackageServiceCreateError.EmptyPackageName)
      }
    }

    "fail when provided package name is not normalized" in new Setup {
      val dcProject: WithId[DCProject] = randomDCProject(status = DCProjectStatus.Idle, packageName = None)
      dcProjectService.get(dcProject.id)(*) shouldReturn future(Right(dcProject))
      forAll(
        Table(
          "name",
          "UPPERCASE",
          "under_score",
          "double--hypens",
          "a space",
          "do.t",
          "-start",
          "end-"
        )
      ) { name =>
        whenReady(service.create(
          dcProject.id,
          Some(name),
          version,
          None,
          None
        )(user)) {
          _ shouldBe Left(DCProjectPackageServiceCreateError.NotNormalizedPackageName)
        }
      }
    }

    "fail when package name is already taken  by another project" in new Setup {
      val dcProject: WithId[DCProject] = randomDCProject(status = DCProjectStatus.Idle, packageName = None)
      dcProjectService.get(dcProject.id)(*) shouldReturn future(Right(dcProject))
      dcProjectService.count(*) shouldReturn future(1)
      dao.count(*) shouldReturn future(0)
      whenReady(service.create(
        dcProject.id,
        Some(packageName),
        version,
        None,
        None
      )(user)) {
        _ shouldBe Left(DCProjectPackageServiceCreateError.NameIsTaken)
      }
    }
  }

  "DCProjectPackageService#List" should {
    "give list of all packages" in new Setup {
      dao.list(*, *, *, Some(SortBy(Created)))(*) shouldReturn
        future(Seq(dcProjectPackage))
      dao.count(*)(*) shouldReturn future(1)
      whenReady(service.list(
        ownerId = None,
        search = None,
        dcProjectId = None,
        orderBy = Seq("created"),
        page = 1,
        pageSize = 1
      )(user)) {
        _ shouldBe Right((Seq(dcProjectPackage), 1))
      }
    }

    "give list of all filtered packages" in new Setup {
      dao.list(*, 1, 1, Some(SortBy(Created)))(*) shouldReturn
        future(Seq(dcProjectPackage))
      dao.count(*)(*) shouldReturn future(1)
      whenReady(service.list(
        ownerId = None,
        search = Some("test"),
        dcProjectId = None,
        orderBy = Seq("created"),
        page = 1,
        pageSize = 1
      )(user)) {
        _ shouldBe Right((Seq(dcProjectPackage), 1))
      }
    }

    "fail when sorting field is unknown" in new Setup {
      whenReady(service.list(
        ownerId = None,
        search = None,
        dcProjectId = None,
        orderBy = Seq("updated"),
        page = 1,
        pageSize = 1
      )(user)) {
        _ shouldBe Left(SortingFieldUnknown)
      }
    }

    "return all packages sorted by version" in new Setup {
      dao.list(*, 1, 1, Some(SortBy(DCProjectPackageDao.Version)))(*) shouldReturn
        future(Seq(dcProjectPackage))
      dao.count(*)(*) shouldReturn future(1)
      whenReady(service.list(
        ownerId = None,
        search = None,
        dcProjectId = None,
        orderBy = Seq("version"),
        page = 1,
        pageSize = 1
      )(user)) {
        _ shouldBe Right((Seq(dcProjectPackage), 1))
      }
    }
  }

  "DCProjectPackageService#Get" should {
    "give the package with give id" in new Setup {
      val randomUser: RegularUser = user.copy(id = UUID.randomUUID())
      dao.get(dcProjectPackageV2.id)(*) shouldReturn future(Some(dcProjectPackageV2))
      whenReady(service.get(
        dcProjectPackageV2.id
      )(randomUser)) {
        _ shouldBe Right(dcProjectPackageV2)
      }
    }

    "return error when package with given id not found" in new Setup {
      dao.get(dcProjectPackage.id)(*) shouldReturn future(None)
      whenReady(service.get(
        dcProjectPackage.id
      )(user)) {
        _ shouldBe Left(DCProjectPackageServiceError.DCProjectPackageNotFound)
      }
    }
  }

  "DCProjectPackageService#Delete" should {
    "delete the package with give id if user is owner" in new Setup {
      cvTLModelPrimitiveDao.listAll(CVTLModelPrimitiveDao.PackageIdIs(dcProjectPackage.id)) shouldReturn future(Seq())
      pipelineOperatorDao.listAll(PipelineOperatorDao.PackageIdIs(dcProjectPackage.id)) shouldReturn future(Seq())
      cvTLModelPrimitiveDao.deleteMany(
        CVTLModelPrimitiveDao.PackageIdIs(dcProjectPackage.id)
      ) shouldReturn future(0)
      pipelineOperatorDao.deleteMany(
        PipelineOperatorDao.PackageIdIs(dcProjectPackage.id)
      ) shouldReturn future(0)
      dao.get(dcProjectPackage.id)(*) shouldReturn future(Some(dcProjectPackage))
      dao.delete(dcProjectPackage.id)(*) shouldReturn future(true)
      whenReady(service.delete(
        dcProjectPackage.id
      )(user)) {
        _ shouldBe Right(())
      }
    }

    "delete the package with published CV TL model primitive and pipeline operator if they are unused" in new Setup {
      val publishedCVTLModelPrimitive = WithId(
        CVTLModelPrimitive(
          packageId = dcProjectPackage.id,
          name = "SCAE",
          isNeural = true,
          moduleName = "ml_lib.feature_extractors.backbones",
          className = "SCAE",
          cvTLModelPrimitiveType = CVTLModelPrimitiveType.UTLP,
          params = Seq(),
          description = None
        ),
        randomString()
      )

      cvTLModelPrimitiveDao.listAll(
        CVTLModelPrimitiveDao.PackageIdIs(dcProjectPackage.id)
      ) shouldReturn future(Seq(publishedCVTLModelPrimitive))
      cvModelDao.count(CVModelDao.OperatorIdIs(publishedCVTLModelPrimitive.id)) shouldReturn future(0)
      experimentDao.count(CVModelPipelineSerializer.OperatorIdIs(publishedCVTLModelPrimitive.id)) shouldReturn future(0)
      cvTLModelPrimitiveDao.deleteMany(
        CVTLModelPrimitiveDao.PackageIdIs(dcProjectPackage.id)
      ) shouldReturn future(1)
      pipelineOperatorDao.listAll(
        PipelineOperatorDao.PackageIdIs(dcProjectPackage.id)
      ) shouldReturn future(Seq(publishedPipelineOperator))
      pipelineDao.count(PipelineDao.OperatorIdIs(publishedPipelineOperator.id)) shouldReturn future(0)
      experimentDao.count(
        GenericExperimentPipelineSerializer.OperatorIdIn(Seq(publishedPipelineOperator.id))
      ) shouldReturn future(0)
      pipelineOperatorDao.deleteMany(
        PipelineOperatorDao.PackageIdIs(dcProjectPackage.id)
      ) shouldReturn future(1)
      dao.get(dcProjectPackage.id)(*) shouldReturn future(Some(dcProjectPackage))
      dao.delete(dcProjectPackage.id)(*) shouldReturn future(true)
      whenReady(service.delete(
        dcProjectPackage.id
      )(user)) {
        _ shouldBe Right(())
      }
    }

    "not allow to delete the package if there is a published CV TL model primitive " +
      "that is used in a CVModel" in new Setup {
      val unusedCVTLModelPrimitive = WithId(
        CVTLModelPrimitive(
          packageId = dcProjectPackage.id,
          name = "SCAE",
          isNeural = true,
          moduleName = "ml_lib.feature_extractors.backbones",
          className = "SCAE",
          cvTLModelPrimitiveType = CVTLModelPrimitiveType.UTLP,
          params = Seq(),
          description = None
        ),
        randomString()
      )
      val usedCVTLModelPrimitive = WithId(
        CVTLModelPrimitive(
          packageId = dcProjectPackage.id,
          name = "SCAE",
          isNeural = true,
          moduleName = "ml_lib.feature_extractors.backbones",
          className = "SCAE",
          cvTLModelPrimitiveType = CVTLModelPrimitiveType.UTLP,
          params = Seq(),
          description = None
        ),
        randomString()
      )
      experimentDao.count(*) shouldReturn future(0)
      cvTLModelPrimitiveDao.listAll(
        CVTLModelPrimitiveDao.PackageIdIs(dcProjectPackage.id)
      ) shouldReturn future(Seq(unusedCVTLModelPrimitive, usedCVTLModelPrimitive))
      cvModelDao.count(CVModelDao.OperatorIdIs(unusedCVTLModelPrimitive.id)) shouldReturn future(0)
      cvModelDao.count(CVModelDao.OperatorIdIs(usedCVTLModelPrimitive.id)) shouldReturn future(1)
      dao.get(dcProjectPackage.id)(*) shouldReturn future(Some(dcProjectPackage))
      whenReady(service.delete(
        dcProjectPackage.id
      )(user)) {
        _ shouldBe Left(DCProjectPackageServiceError.DCProjectPackageInUse)
      }
    }

    "not allow to delete the package if there is a published CV TL model primitive " +
      "that is used in an experiment" in new Setup {
      val unusedCVTLModelPrimitive = WithId(
        CVTLModelPrimitive(
          packageId = dcProjectPackage.id,
          name = "SCAE",
          isNeural = true,
          moduleName = "ml_lib.feature_extractors.backbones",
          className = "SCAE",
          cvTLModelPrimitiveType = CVTLModelPrimitiveType.UTLP,
          params = Seq(),
          description = None
        ),
        randomString()
      )
      val usedCVTLModelPrimitive = WithId(
        CVTLModelPrimitive(
          packageId = dcProjectPackage.id,
          name = "SCAE",
          isNeural = true,
          moduleName = "ml_lib.feature_extractors.backbones",
          className = "SCAE",
          cvTLModelPrimitiveType = CVTLModelPrimitiveType.UTLP,
          params = Seq(),
          description = None
        ),
        randomString()
      )
      cvModelDao.count(*) shouldReturn future(0)
      cvTLModelPrimitiveDao.listAll(
        CVTLModelPrimitiveDao.PackageIdIs(dcProjectPackage.id)
      ) shouldReturn future(Seq(unusedCVTLModelPrimitive, usedCVTLModelPrimitive))
      experimentDao.count(CVModelPipelineSerializer.OperatorIdIs(unusedCVTLModelPrimitive.id)) shouldReturn future(0)
      experimentDao.count(CVModelPipelineSerializer.OperatorIdIs(usedCVTLModelPrimitive.id)) shouldReturn future(1)
      dao.get(dcProjectPackage.id)(*) shouldReturn future(Some(dcProjectPackage))
      whenReady(service.delete(
        dcProjectPackage.id
      )(user)) {
        _ shouldBe Left(DCProjectPackageServiceError.DCProjectPackageInUse)
      }
    }

    "not allow to delete the package if there is a published pipeline operator " +
      "that is used in a pipeline" in new Setup {
      val unusedPipelineOperator = WithId(
        PipelineOperator(
          name = "SCAE",
          description = None,
          category = Some("OTHER"),
          moduleName = "ml_lib.feature_extractors.backbones",
          className = "SCAE",
          packageId = dcProjectPackage.id,
          params = Seq(),
          inputs = Seq(),
          outputs = Seq()
        ),
        randomString()
      )
      val usedPipelineOperator = WithId(
        PipelineOperator(
          name = "SCAE",
          description = None,
          category = Some("OTHER"),
          moduleName = "ml_lib.feature_extractors.backbones",
          className = "SCAE",
          packageId = dcProjectPackage.id,
          params = Seq(),
          inputs = Seq(),
          outputs = Seq()
        ),
        randomString()
      )
      cvTLModelPrimitiveDao.listAll(CVTLModelPrimitiveDao.PackageIdIs(dcProjectPackage.id)) shouldReturn future(Seq())
      pipelineOperatorDao.listAll(
        PipelineOperatorDao.PackageIdIs(dcProjectPackage.id)
      ) shouldReturn future(Seq(unusedPipelineOperator, usedPipelineOperator))
      pipelineDao.count(PipelineDao.OperatorIdIs(unusedPipelineOperator.id)) shouldReturn future(0)
      pipelineDao.count(PipelineDao.OperatorIdIs(usedPipelineOperator.id)) shouldReturn future(1)
      dao.get(dcProjectPackage.id)(*) shouldReturn future(Some(dcProjectPackage))
      whenReady(service.delete(
        dcProjectPackage.id
      )(user)) {
        _ shouldBe Left(DCProjectPackageServiceError.DCProjectPackageInUse)
      }
    }

    "not allow to delete the package if there is a pipeline operator that is used in an experiment" in new Setup {
      val unusedPipelineOperator = WithId(
        PipelineOperator(
          name = "SCAE",
          description = None,
          category = Some("OTHER"),
          moduleName = "ml_lib.feature_extractors.backbones",
          className = "SCAE",
          packageId = dcProjectPackage.id,
          params = Seq(),
          inputs = Seq(),
          outputs = Seq()
        ),
        randomString()
      )
      val usedPipelineOperator = WithId(
        PipelineOperator(
          name = "SCAE",
          description = None,
          category = Some("OTHER"),
          moduleName = "ml_lib.feature_extractors.backbones",
          className = "SCAE",
          packageId = dcProjectPackage.id,
          params = Seq(),
          inputs = Seq(),
          outputs = Seq()
        ),
        randomString()
      )
      cvTLModelPrimitiveDao.listAll(CVTLModelPrimitiveDao.PackageIdIs(dcProjectPackage.id)) shouldReturn future(Seq())
      pipelineOperatorDao.listAll(
        PipelineOperatorDao.PackageIdIs(dcProjectPackage.id)
      ) shouldReturn future(Seq(unusedPipelineOperator, usedPipelineOperator))
      pipelineDao.count(PipelineDao.OperatorIdIs(unusedPipelineOperator.id)) shouldReturn future(0)
      pipelineDao.count(PipelineDao.OperatorIdIs(usedPipelineOperator.id)) shouldReturn future(0)
      experimentDao.count(
        GenericExperimentPipelineSerializer.OperatorIdIn(Seq(unusedPipelineOperator.id, usedPipelineOperator.id))
      ) shouldReturn future(1)
      dao.get(dcProjectPackage.id)(*) shouldReturn future(Some(dcProjectPackage))
      whenReady(service.delete(
        dcProjectPackage.id
      )(user)) {
        _ shouldBe Left(DCProjectPackageServiceError.DCProjectPackageInUse)
      }
    }

    "not allow to delete the package with give id if user is not the owner" in new Setup {
      val randomUser: User = user.copy(id = UUID.randomUUID())
      dao.get(dcProjectPackage.id)(*) shouldReturn future(Some(dcProjectPackage))
      whenReady(service.delete(
        dcProjectPackage.id
      )(randomUser)) {
        _ shouldBe Left(DCProjectPackageServiceError.AccessDenied)
      }
    }
  }

  "DCProjectPackageService#listPackageNames" should {
    "return list of packages names" in new Setup {
      dao.listPackageNames(*)(*) shouldReturn future(Seq(packageName, globalPackageName))
      whenReady(service.listPackageNames(user)) { packageNames =>
        packageNames should contain theSameElementsAs Seq(packageName, globalPackageName)
      }
    }
  }

  "DCProjectPackageService#listPackageArtifacts" should {
    "return list of package artifacts" in new Setup {
      dao.listAll(*, None)(*) shouldReturn future(
        Seq(dcProjectPackage, dcProjectPackageV2)
      )
      val url = "http://package-1.0.0.whl?signed"
      val urlV2 = "http://package-2.0.0.whl?signed"
      packagesStorage.getExternalUrl(packageLocation, *) shouldReturn url
      packagesStorage.getExternalUrl(packageV2Location, *) shouldReturn urlV2
      packagesStorage.split(packageLocation) shouldReturn packageLocation.split("/")
      packagesStorage.split(packageV2Location) shouldReturn packageV2Location.split("/")

      whenReady(service.listPackageArtifacts(packageName)(user)) { result =>
        result.isRight shouldBe true
        val artifacts = result.right.get
        artifacts should contain theSameElementsAs Seq(
          DCProjectPackageArtifact(packageFile, url),
          DCProjectPackageArtifact(packageV2File, urlV2)
        )
      }
    }

    "return error if package doesn't exist" in new Setup {
      dao.listAll(*, None)(*) shouldReturn future(Nil)
      whenReady(service.listPackageArtifacts(packageName)(user)) { result =>
        result shouldBe Left(DCProjectPackageNotFound)
      }
    }
  }

  "DCProjectPackageService#publish" should {

    "successfully publish package" in new Setup {
      val categoryId = randomString()
      val pipelineOperatorParams: Seq[PipelineOperatorPublishParams] = Seq(PipelineOperatorPublishParams(
        id = publishedPipelineOperator.id,
        categoryId = categoryId
      ))
      dao.get(dcProjectPackage.id) shouldReturn future(Some(dcProjectPackage))
      dao.update(dcProjectPackage.id, *) shouldReturn future(Some(updatedDCProjectpackage))
      pipelineOperatorDao.listAll(
        PipelineOperatorDao.PackageIdIs(dcProjectPackage.id)
      ) shouldReturn future(Seq(publishedPipelineOperator))
      pipelineOperatorDao.update(publishedPipelineOperator.id, *) shouldReturn future(Some(publishedPipelineOperator))
      categoryDao.count(CategoryIdIs(categoryId)) shouldReturn future(1)
      cvTLModelPrimitiveDao.listAll(CVTLModelPrimitiveDao.PackageIdIs(dcProjectPackage.id)) shouldReturn future(Seq())
      whenReady(service.publish(
        dcProjectPackage.id,
        pipelineOperatorParams
      )(user)) {
        _ shouldBe Right(ExtendedPackageResponse(updatedDCProjectpackage, Seq(), Seq(publishedPipelineOperator)))
      }
    }

    "fail when provided params are not valid" in new Setup {
      val pipelineOperatorParams: Seq[PipelineOperatorPublishParams] = Seq(PipelineOperatorPublishParams(
        id = publishedPipelineOperator.id,
        categoryId = "categoryId"
      ))
      dao.get(dcProjectPackage.id) shouldReturn future(Some(dcProjectPackage))
      categoryDao.count(CategoryIdIs("categoryId")) shouldReturn future(0)
      whenReady(service.publish(
        dcProjectPackage.id,
        pipelineOperatorParams
      )(user)) {
        _ shouldBe Left(DCProjectPackageServiceError.CategoryNotFound("categoryId"))
      }
    }

  }

}

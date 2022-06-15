package baile.services.dcproject

import java.util.UUID

import akka.event.LoggingAdapter
import baile.dao.asset.Filters.SearchQuery
import baile.dao.cv.model.tlprimitives.CVTLModelPrimitiveDao
import baile.dao.cv.model.{ CVModelDao, CVModelPipelineSerializer }
import baile.dao.dcproject.DCProjectDao.PackageNameIs
import baile.dao.dcproject.DCProjectPackageDao
import baile.dao.dcproject.DCProjectPackageDao._
import baile.dao.experiment.ExperimentDao
import baile.dao.pipeline.category.CategoryDao
import baile.dao.pipeline.{ GenericExperimentPipelineSerializer, PipelineDao, PipelineOperatorDao }
import baile.daocommons.WithId
import baile.daocommons.filters.{ Filter, IdIs, TrueFilter }
import baile.daocommons.sorting.Field
import baile.domain.asset.AssetType
import baile.domain.common.Version
import baile.domain.cv.model.tlprimitives.CVTLModelPrimitive
import baile.domain.dcproject.{ DCProject, DCProjectPackage, DCProjectPackageArtifact, DCProjectStatus }
import baile.domain.pipeline.PipelineOperator
import baile.domain.usermanagement.User
import baile.services.common.EntityService
import baile.services.common.EntityService.WithSortByField
import baile.services.cortex.job.CortexJobService
import baile.services.dcproject.DCProjectPackageService.DCProjectPackageServiceError._
import baile.services.dcproject.DCProjectPackageService.DCProjectPackageServiceCreateError._
import baile.services.dcproject.DCProjectPackageService.{
  DCProjectPackageServiceCreateError,
  DCProjectPackageServiceError,
  ExtendedPackageResponse,
  PipelineOperatorPublishParams
}
import baile.services.process.ProcessService
import baile.services.remotestorage.RemoteStorageService
import baile.utils.validation.Option._
import cats.data.EitherT
import cats.implicits._
import cortex.api.job.project.`package`.ProjectPackageRequest

import scala.concurrent.{ ExecutionContext, Future }

class DCProjectPackageService(
  protected val dao: DCProjectPackageDao,
  protected val cvTLModelPrimitiveDao: CVTLModelPrimitiveDao,
  protected val cvModelDao: CVModelDao,
  protected val pipelineOperatorDao: PipelineOperatorDao,
  protected val experimentDao: ExperimentDao,
  protected val pipelineDao: PipelineDao,
  protected val dcProjectService: DCProjectService,
  protected val cortexJobService: CortexJobService,
  protected val processService: ProcessService,
  protected val packageStorage: RemoteStorageService,
  protected val packageStorageKeyPrefix: String,
  protected val categoryDao: CategoryDao
)(
  implicit val ec: ExecutionContext,
  val logger: LoggingAdapter
) extends EntityService[DCProjectPackage, DCProjectPackageServiceError]
  with WithSortByField[DCProjectPackage, DCProjectPackageServiceError] {

  override val notFoundError: DCProjectPackageServiceError = DCProjectPackageNotFound
  override val sortingFieldNotFoundError: DCProjectPackageServiceError = SortingFieldUnknown

  override protected val findField: String => Option[Field] = Map(
    "name" -> DCProjectPackageDao.Name,
    "created" -> DCProjectPackageDao.Created,
    "version" -> DCProjectPackageDao.Version
  ).get

  type DCProjectPackageErrorOr[R] = Future[Either[DCProjectPackageServiceError, R]]
  type DCProjectPackageCreateErrorOr[R] = Future[Either[DCProjectPackageServiceCreateError, R]]

  def create(
    dcProjectId: String,
    name: Option[String],
    version: Version,
    description: Option[String],
    analyzePipelineOperators: Option[Boolean]
  )(implicit user: User): DCProjectPackageCreateErrorOr[WithId[DCProject]] = {

    def validatePackageNameIsRequired(dcProject: DCProject): Either[DCProjectPackageServiceCreateError, Unit] = {
      dcProject.packageName match {
        case None => Either.cond(name.isDefined, (), PackageNameIsRequired)
        case Some(value) => Either.cond(
          name.forall(_ == value),
          (),
          PackageNameAlreadyDefined(value)
        )
      }
    }

    def updateDCProject(packageName: String): Future[WithId[DCProject]] = {
      dcProjectService.update(
        dcProjectId,
        status = DCProjectStatus.Building,
        packageName = Some(packageName)
      ).map(_.getOrElse(throw new RuntimeException("Unexpectedly not found project to update")))
    }

    def checkVersionIsGreater(
      latestVersion: Version
    ): Either[DCProjectPackageServiceCreateError, Unit] = {
      import Ordering.Implicits._
      Either.cond(
        version > latestVersion,
        (),
        VersionNotGreater
      )
    }

    val result = for {
      _ <- EitherT.cond[Future](version.suffix.isEmpty, (), InvalidPackageVersion)
      project <- EitherT(dcProjectService.get(dcProjectId)).leftMap(_ => DCProjectNotFound)
      _ <- EitherT.cond[Future](project.entity.status == DCProjectStatus.Idle, (), DCProjectNotIdle)
      _ <- EitherT.fromEither[Future](validatePackageNameIsRequired(project.entity))
      _ <- EitherT(name.validate(validatePackageName(_, project)))
      optionalLatestVersion = project.entity.latestPackageVersion
      _ <- EitherT.fromEither[Future](optionalLatestVersion.validate { latestVersion =>
        checkVersionIsGreater(latestVersion)
      })
      packageName = project.entity.packageName getOrElse name.get
      packageTargetPrefix = packageStorage.path(packageStorageKeyPrefix, s"packages/${ user.id }/$dcProjectId")
      request = ProjectPackageRequest(
        projectFilesPath = dcProjectService.buildFullStoragePath(project.entity, ""),
        name = packageName,
        version = version.toString,
        targetPrefix = packageTargetPrefix
      )
      jobId <- EitherT.right[DCProjectPackageServiceCreateError](cortexJobService.submitJob(request, user.id))
      _ <- EitherT.right[DCProjectPackageServiceCreateError](processService.startProcess(
        jobId = jobId,
        targetId = project.id,
        targetType = AssetType.DCProject,
        handlerClass = classOf[DCProjectBuildResultHandler],
        meta = DCProjectBuildResultHandler.Meta(
          dcProjectId = project.id,
          name = packageName,
          version = version,
          userId = user.id,
          packageAlreadyExists = project.entity.packageName.isDefined,
          description = description,
          analyzePipelineOperators = analyzePipelineOperators.getOrElse(true)
        ),
        userId = user.id
      ))
      updatedDCProject <- EitherT.right[DCProjectPackageServiceCreateError](updateDCProject(packageName))
    } yield updatedDCProject

    result.value
  }

  def publish(
    packageId: String,
    pipelineOperatorParams: Seq[PipelineOperatorPublishParams]
  )(implicit user: User): DCProjectPackageErrorOr[ExtendedPackageResponse] = {

    def assignCategoryToPipelineOperators(
      pipelineOperators: Seq[WithId[PipelineOperator]]
    ): Future[Unit] = {
      Future.sequence(pipelineOperatorParams map { param =>
        val pipelineOperator = pipelineOperators.find(_.id == param.id)
          .getOrElse(throw new RuntimeException(s"unexpectedly not found operator ${ param.id }"))
        pipelineOperatorDao.update(pipelineOperator.id, _.copy(category = Some(param.categoryId)))
      }).map(_ => ())
    }

    def validateParamsProvidedForAllOperators(
      pipelineOperators: Seq[WithId[PipelineOperator]]
    ): EitherT[Future, DCProjectPackageServiceError, Unit] = {
      val requiredPipelineOperators = pipelineOperators.filter(_.entity.category.isEmpty).map(_.id).toSet
      val diff =  requiredPipelineOperators diff pipelineOperatorParams.map(_.id).toSet
      EitherT.cond[Future](
        diff.isEmpty,
        (),
        CategoryNotProvided(diff): DCProjectPackageServiceError
      )
    }

    type EitherTOr[R] = EitherT[Future, DCProjectPackageServiceError, R]

    def validateCategories: EitherT[Future, DCProjectPackageServiceError, Unit] = {

      def validateCategory(categoryId: String): Future[Either[DCProjectPackageServiceError, Unit]] = {
        categoryDao.count(CategoryDao.CategoryIdIs(categoryId)) map { count =>
          Either.cond(
            count > 0,
            (),
            CategoryNotFound(categoryId): DCProjectPackageServiceError
          )
        }
      }

      pipelineOperatorParams.map(_.categoryId)
        .toSet
        .toList
        .traverse[EitherTOr, Unit](category => EitherT(validateCategory(category)))
        .map(_ => ())
    }

    val result = for {
      dcProjectPackage <- EitherT(get(packageId))
      _ <- EitherT(ensureOwnership(dcProjectPackage, user))
      _ <- EitherT.cond[Future](!dcProjectPackage.entity.isPublished, (), DCProjectPackageAlreadyPublished)
      _ <- validateCategories
      operators <- EitherT.right[DCProjectPackageServiceError](getPipelineOperators(packageId))
      _ <- validateParamsProvidedForAllOperators(operators)
      _ <- EitherT.right[DCProjectPackageServiceError](assignCategoryToPipelineOperators(operators))
      updatedPackage <- EitherT(update(packageId, _.copy(isPublished = true)))
      modelPrimitives <- EitherT.right[DCProjectPackageServiceError](
        cvTLModelPrimitiveDao.listAll(CVTLModelPrimitiveDao.PackageIdIs(packageId))
      )
      pipelineOperators <- EitherT.right[DCProjectPackageServiceError](
        pipelineOperatorDao.listAll(PipelineOperatorDao.PackageIdIs(packageId))
      )
    } yield ExtendedPackageResponse(updatedPackage, modelPrimitives, pipelineOperators)
    result.value
  }

  def list(
    ownerId: Option[UUID],
    search: Option[String],
    dcProjectId: Option[String],
    orderBy: Seq[String],
    page: Int,
    pageSize: Int
  )(implicit user: User): DCProjectPackageErrorOr[(Seq[WithId[DCProjectPackage]], Int)] = {
    val ownerIdFilter = ownerId.fold[Filter](TrueFilter)(OwnerIdIs)
    val dcProjectIdFilter = dcProjectId.fold[Filter](TrueFilter)(DCProjectIdIs(_))
    val searchFilter = search.fold[Filter](TrueFilter)(SearchQuery)

    this.list(
      filter = ownerIdFilter && dcProjectIdFilter && searchFilter,
      orderBy = orderBy,
      page = page,
      pageSize = pageSize
    )
  }

  def listPackageNames(implicit user: User): Future[Seq[String]] = {
    for {
      canReadFilter <- prepareCanReadFilter(user)
      packageNames <- dao.listPackageNames(canReadFilter && HasLocation)
    } yield packageNames
  }

  def listPackageArtifacts(
    packageName: String
  )(implicit user: User): DCProjectPackageErrorOr[Seq[DCProjectPackageArtifact]] = {
    val result = for {
      canReadFilter <- EitherT.right[DCProjectPackageServiceError](prepareCanReadFilter(user))
      packages <- EitherT(listAll(NameIs(packageName) && HasLocation && canReadFilter, Nil))
      _ <- EitherT.cond[Future](
        packages.nonEmpty,
        (),
        DCProjectPackageNotFound: DCProjectPackageServiceError
      )
    } yield {
      packages.flatMap(_.entity.location).map { location =>
        DCProjectPackageArtifact(
          filename = packageStorage.split(location).last,
          url = packageStorage.getExternalUrl(location)
        )
      }
    }

    result.value
  }

  def signPackage(projectPackage: WithId[DCProjectPackage]): WithId[DCProjectPackage] = {
    val url = projectPackage.entity.location.map(packageStorage.getExternalUrl(_))
    WithId(projectPackage.entity.copy(location = url), projectPackage.id)
  }

  def getPackageWithPipelineOperators(
    id: String
  )(implicit user: User): DCProjectPackageErrorOr[ExtendedPackageResponse] = {
    val result = for {
      projectPackage <- EitherT(get(id))
      modelPrimitives <- EitherT.right[DCProjectPackageServiceError](
        cvTLModelPrimitiveDao.listAll(CVTLModelPrimitiveDao.PackageIdIs(id))
      )
      pipelineOperators <- EitherT.right[DCProjectPackageServiceError](
        pipelineOperatorDao.listAll(PipelineOperatorDao.PackageIdIs(id))
      )
    } yield ExtendedPackageResponse(projectPackage, modelPrimitives, pipelineOperators)
    result.value
  }

  private def getPipelineOperators(packageId: String): Future[Seq[WithId[PipelineOperator]]] =
    pipelineOperatorDao.listAll(PipelineOperatorDao.PackageIdIs(packageId))

  private[services] def loadPackageMandatory(id: String): Future[WithId[DCProjectPackage]] =
    dao.get(id).map(_.getOrElse(throw new RuntimeException(
      s"Unexpectedly not found package $id in storage"
    )))

  private[services] def getPackageByNameAndVersion(
    packageName: String,
    version: Option[Version]
  )(implicit user: User): Future[Option[WithId[DCProjectPackage]]] = {
    for {
      canReadFilter <- prepareCanReadFilter(user)
      packages <- version match {
        case Some(packageVersion) =>
          dao.listAll((NameIs(packageName) && VersionIs(packageVersion) || NameIs(packageName) && HasNoVersion) &&
            canReadFilter
          )
        case None =>
          dao.listAll((NameIs(packageName) && HasNoVersion) && canReadFilter)
      }
      maybePackage = if (packages.length <= 1) {
        packages.headOption
      } else {
        throw new RuntimeException(
          s"Unexpectedly found multiple packages by name: [$packageName] and/or version: [$version] in storage"
        )
      }

    } yield maybePackage
  }

  override protected def ensureCanDelete(
    projectPackage: WithId[DCProjectPackage],
    user: User
  ): DCProjectPackageErrorOr[Unit] = {
    val result = for {
      _ <- EitherT.cond[Future](
        !projectPackage.entity.isPublished,
        (),
        CannotDeletePublishedPackage: DCProjectPackageServiceError
      )
      _ <- EitherT(ensureOwnership(projectPackage, user))
      cvTLModelPrimitives <- EitherT.right[DCProjectPackageServiceError](
        cvTLModelPrimitiveDao.listAll(CVTLModelPrimitiveDao.PackageIdIs(projectPackage.id))
      )
      numberOfDependentCVModels <- EitherT.right[DCProjectPackageServiceError](
        cvTLModelPrimitives.foldLeft(0.pure[Future]) {
          case (count, p) => cvModelDao.count(CVModelDao.OperatorIdIs(p.id)) |+| count
        }
      )
      numberOfDependentExperiments <- EitherT.right[DCProjectPackageServiceError](
        cvTLModelPrimitives.foldLeft(0.pure[Future]) {
          case (count, p) => experimentDao.count(CVModelPipelineSerializer.OperatorIdIs(p.id)) |+| count
        }
      )
      _ <- EitherT.cond[Future](
        numberOfDependentCVModels == 0,
        (),
        DCProjectPackageInUse
      )
      _ <- EitherT.cond[Future](
        numberOfDependentExperiments == 0,
        (),
        DCProjectPackageInUse
      )
      pipelineOperators <- EitherT.right[DCProjectPackageServiceError](
        pipelineOperatorDao.listAll(PipelineOperatorDao.PackageIdIs(projectPackage.id))
      )
      numberOfDependentPipelines <- EitherT.right[DCProjectPackageServiceError](
        pipelineOperators.foldLeft(0.pure[Future]) {
          case (count, p) => pipelineDao.count(PipelineDao.OperatorIdIs(p.id)) |+| count
        }
      )
      _ <- EitherT.cond[Future](
        numberOfDependentPipelines == 0,
        (),
        DCProjectPackageInUse
      )
      numberOfDependentGenericExperiments <- EitherT.right[DCProjectPackageServiceError](
        if (pipelineOperators.nonEmpty) {
          experimentDao.count(GenericExperimentPipelineSerializer.OperatorIdIn(pipelineOperators.map(_.id)))
        }
        else {
          0.pure[Future]
        }
      )
      _ <- EitherT.cond[Future](
        numberOfDependentGenericExperiments == 0,
        (),
        DCProjectPackageInUse: DCProjectPackageServiceError
      )
    } yield ()
    result.value
  }

  override protected def ensureCanUpdate(
    projectPackage: WithId[DCProjectPackage],
    user: User
  ): DCProjectPackageErrorOr[Unit] = ensureOwnership(projectPackage, user)

  override protected def ensureCanRead(
    projectPackage: WithId[DCProjectPackage],
    user: User
  ): DCProjectPackageErrorOr[Unit] =
    if (projectPackage.entity.isPublished || projectPackage.entity.ownerId.contains(user.id)) {
      Future.successful(().asRight)
    }
    else {
      Future.successful(AccessDenied.asLeft)
    }

  override protected def prepareCanReadFilter(user: User): Future[Filter] =
    Future.successful(IsPublished || OwnerIdIs(user.id))

  override protected def preDelete(
    projectPackage: WithId[DCProjectPackage]
  )(implicit user: User): DCProjectPackageErrorOr[Unit] = {
    val result = for {
      _ <- EitherT(super.preDelete(projectPackage))
      _ <- EitherT.right[DCProjectPackageServiceError](
        cvTLModelPrimitiveDao.deleteMany(CVTLModelPrimitiveDao.PackageIdIs(projectPackage.id))
      )
      _ <- EitherT.right[DCProjectPackageServiceError](
        pipelineOperatorDao.deleteMany(PipelineOperatorDao.PackageIdIs(projectPackage.id))
      )
    } yield ()

    result.value
  }

  private[services] def get(filter: Filter): Future[Option[WithId[DCProjectPackage]]] = {
    dao.get(filter)
  }

  private def ensureOwnership(
    projectPackage: WithId[DCProjectPackage],
    user: User
  ): DCProjectPackageErrorOr[Unit] = {
    Future.successful {
      projectPackage.entity.ownerId match {
        case Some(ownerId) if ownerId == user.id => ().asRight
        case _ => AccessDenied.asLeft
      }
    }
  }

  private def packageNameIsNormalized(packageName: String): Boolean =
    packageName.matches("^[a-z0-9]+(-[a-z0-9]+)*$")

  private def validatePackageName(
    packageName: String,
    dcProject: WithId[DCProject]
  ): Future[Either[DCProjectPackageServiceCreateError, Unit]] = {
    if (packageName.isEmpty) {
      Future.successful(EmptyPackageName.asLeft)
    } else if (!packageNameIsNormalized(packageName)) {
      Future.successful(NotNormalizedPackageName.asLeft)
    } else if (dcProject.entity.packageName.contains(packageName)) {
      Future.successful(().asRight)
    } else {
      for {
        countInProjects <- dcProjectService.count(PackageNameIs(packageName) && !IdIs(dcProject.id))
        countInPackagesWithoutProject <- dao.count(DCProjectIdIs(None) && NameIs(packageName))
      } yield {
        if (countInProjects > 0 || countInPackagesWithoutProject > 0) NameIsTaken.asLeft
        else ().asRight
      }
    }
  }

}

object DCProjectPackageService {

  case class PipelineOperatorPublishParams(
    id: String,
    categoryId: String
  )

  case class ExtendedPackageResponse(
    dcProjectPackage: WithId[DCProjectPackage],
    primitives: Seq[WithId[CVTLModelPrimitive]],
    pipelineOperators: Seq[WithId[PipelineOperator]]
  )

  sealed trait DCProjectPackageServiceCreateError

  object DCProjectPackageServiceCreateError {

    case class PackageNameAlreadyDefined(name: String) extends DCProjectPackageServiceCreateError

    case object PackageNameIsRequired extends DCProjectPackageServiceCreateError

    case object VersionNotGreater extends DCProjectPackageServiceCreateError

    case object DCProjectNotFound extends DCProjectPackageServiceCreateError

    case object NameIsTaken extends DCProjectPackageServiceCreateError

    case object DCProjectNotIdle extends DCProjectPackageServiceCreateError

    case object EmptyPackageName extends DCProjectPackageServiceCreateError

    case object NotNormalizedPackageName extends DCProjectPackageServiceCreateError

    case object InvalidPackageVersion extends DCProjectPackageServiceCreateError
  }

  sealed trait DCProjectPackageServiceError

  object DCProjectPackageServiceError {

    case object AccessDenied extends DCProjectPackageServiceError

    case object DCProjectPackageNotFound extends DCProjectPackageServiceError

    case object SortingFieldUnknown extends DCProjectPackageServiceError

    case object DCProjectPackageInUse extends DCProjectPackageServiceError

    case object CannotDeletePublishedPackage extends DCProjectPackageServiceError

    case object DCProjectPackageAlreadyPublished extends DCProjectPackageServiceError

    case class CategoryNotProvided(operatorIds: Set[String]) extends DCProjectPackageServiceError

    case class CategoryNotFound(category: String) extends DCProjectPackageServiceError

  }

}

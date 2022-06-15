package baile.dao.mongo.migrations

import baile.dao.mongo.migrations.history._
import com.typesafe.config.{ Config, ConfigFactory }
import org.mongodb.scala.MongoClient

import scala.concurrent.Await
import scala.concurrent.duration.Duration

// scalastyle:off field.name
object MongoMigrationApp extends App {

  val conf: Config = ConfigFactory.load()
  val migrationsCollectionName = conf.getString("mongo.migrations.collection-name")
  val mongoClient = MongoClient(conf.getString("mongo.url"))
  val mongoDatabase = mongoClient.getDatabase(conf.getString("mongo.db-name"))
  val migrationMetaDao = new MigrationMetaDao(migrationsCollectionName, mongoDatabase)

  val migrations: List[MongoMigration] = List(
    CreateInitialCollections,
    RenameCVCollectionsFields,
    UpdateCVFeatureExtractorsSummaries,
    UpdateCVFeatureExtractorsArchitecture,
    UpdateCVModelsType,
    UpdateProjectsAssets,
    UpdateCVModelsClassifierType,
    SetCVModelFeatureExtractorArchitecture,
    UpdateProjectsWithFolders,
    CreatePipelineParams,
    CreateOwnerIdIndexForProject,
    MergeCVFeatureExtractorsToCVModels,
    UpdateCVModelsTypeValues,
    CreateCVModelTypeAndFEArchitectureCollections,
    InsertValuesIntoFEArchitectures,
    InsertValuesIntoClassifiers,
    InsertValueIntoLocalizers,
    InsertValueIntoAutoEncoders,
    CreateDCProjectsCollection,
    CreateAlbumIdIndexForPictures,
    CreateDCProjectSessionsCollection,
    CreateDCProjectIdIndexForDCProjectSession,
    UpdatePipelineParams,
    CreatePackageNameIndexForDCProject,
    CreateDCProjectPackagesCollectionWithDCProjectIdIndex,
    CreatePipelineOperatorCollectionWithPackageIdIndex,
    SetAuxiliaryOnCompleteForProcesses,
    InsertPipelineOperatorsOfSystemPackageAndUpdateCVModels,
    UpdateClassifiersWithPipelineParamsDefinition,
    CreateModuleNameIndexOnPipelineOperator,
    CreateNameIndexForDCProjectPackage,
    CreateExperimentsCollection,
    MoveCVModelsFieldsToExperiments,
    ReplaceCVModelTypeByTLModel,
    MoveTabularModelsFieldsToExperiments,
    RenamePipelineOperatorsToCVTLModelPrimitivesAndCreateNewCollectionsForPipeline,
    SetClassReferenceForTabularModel,
    InsertSystemPipelineOperators,
    RemoveSystemPackageVersion,
    TurnTableStatisticStatusFromPendingToErrorIfProcessDoesNotExist,
    CreateDatasetsCollection,
    CreateAndFillCategoryCollection,
    SetOtherCategoryForPipelineOperators,
    SetSpecificCategoryForSystemPipelineOperators,
    AddFieldRequiredToPipelineOperatorInput,
    AddPipelineParametersToPipelineSteps,
    AddIsPublishedFieldInDCProjectPackage,
    UpdateCategoriesCollection,
    AddTrainParametersToRfbNetDetector,
    UpdateDCProjectStatus,
    UpdateHandlerNameForDCProjectInExistingProcesses,
    UpdateCompletedProcessProgressToOne,
    RenameSystemPipelineOperatorsLoadingAssets,
    AddWaitForCompleteParameterToSaveTableOperator
  )

  val migrationsExecutor: MigrationsExecutor = new MigrationsExecutor(migrationMetaDao)

  Await.result(
    migrationsExecutor.migrate(migrations)(db = mongoDatabase, ec = scala.concurrent.ExecutionContext.global),
    Duration.Inf
  )

}

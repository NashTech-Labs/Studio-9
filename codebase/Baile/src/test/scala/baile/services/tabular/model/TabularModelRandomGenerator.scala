package baile.services.tabular.model

import java.time.Instant
import java.util.UUID

import baile.daocommons.WithId
import baile.domain.common.{ ClassReference, CortexModelReference }
import baile.domain.tabular.model.{ ModelColumn, TabularModel, TabularModelStatus }
import baile.RandomGenerators._
import baile.domain.table.Column
import baile.services.table.util.TableRandomGenerator

object TabularModelRandomGenerator {

  // scalastyle:off parameter.number
  def randomModel(
    ownerId: UUID = UUID.randomUUID(),
    status: TabularModelStatus = randomOf(
      TabularModelStatus.Predicting,
      TabularModelStatus.Training,
      TabularModelStatus.Active,
      TabularModelStatus.Cancelled,
      TabularModelStatus.Error
    ),
    name: String = randomString(),
    inLibrary: Boolean = randomBoolean(),
    inputTableId: String = randomString(),
    outputTableId: String = randomString(),
    predictorColumns: Seq[ModelColumn] = Seq.fill(randomInt(1, 10))(randomModelColumn()),
    samplingWeightColumn: Option[ModelColumn] = randomOf(None, Some(randomModelColumn())),
    responseColumn: ModelColumn = randomModelColumn(),
    holdOutInputTableId: Option[String] = None,
    outOfTimeInputTableId: Option[String] = None,
    classReference: ClassReference = ClassReference(
      packageId = randomString(),
      moduleName = randomString(),
      className = randomString()
    ),
    cortexModelReference: Option[CortexModelReference] = None,
    classNames: Option[Seq[String]] = None,
    description: Option[String] = None,
    experimentId: Option[String] = None
  ): WithId[TabularModel] =
    WithId(
      TabularModel(
        ownerId = ownerId,
        name = name,
        predictorColumns = predictorColumns,
        responseColumn = responseColumn,
        classNames = classNames,
        classReference = classReference,
        cortexModelReference = cortexModelReference,
        inLibrary = inLibrary,
        status = status,
        created = Instant.now,
        updated = Instant.now,
        description = description,
        experimentId = experimentId
      ),
      randomString()
    )
  // scalastyle:on parameter.number

  def randomModelColumn(): ModelColumn = buildModelColumn(TableRandomGenerator.randomColumn())

  def buildModelColumn(column: Column): ModelColumn =
    ModelColumn(
      name = column.name,
      displayName = column.name,
      dataType = column.dataType,
      variableType = column.variableType
    )

}

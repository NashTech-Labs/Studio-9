package baile.services.pipeline

import baile.{ ExtendedBaseSpec, RandomGenerators }
import baile.daocommons.WithId
import baile.domain.asset.AssetType
import baile.domain.pipeline.PipelineParams.PipelineParams
import baile.domain.pipeline.{ PipelineParams, _ }
import baile.services.pipeline.PipelineValidationError._
import cats.implicits._
import org.scalatest.prop.TableDrivenPropertyChecks

class PipelineValidatorSpec extends ExtendedBaseSpec with TableDrivenPropertyChecks {

  private val baseModelType = ComplexDataType("BaseModel", Seq.empty, Seq.empty)

  private val albumApiType = ComplexDataType("api.Album", Seq(baseModelType), Seq.empty)
  private val albumLibType = ComplexDataType("lib.Album", Seq(albumApiType), Seq.empty)

  private val tableApiType = ComplexDataType("api.Table", Seq(baseModelType), Seq.empty)
  private val tableLibType = ComplexDataType("lib.Table", Seq(tableApiType), Seq.empty)

  private val albumAssetReferenceTypeInfo = AssetParameterTypeInfo(AssetType.Album)
  private val loadAlbumOperator = WithId(
    PipelineOperator(
      name = "Load album",
      description = None,
      category = Some("OTHER"),
      className = "LoadAlbum",
      moduleName = "deepcortex.pipelines.operators.load_album",
      packageId = "deepcortex-package-id",
      inputs = Seq.empty,
      outputs = Seq(
        PipelineOperatorOutput(
          description = None,
          `type` = albumLibType
        )
      ),
      params = Seq(
        OperatorParameter(
          name = "album_id",
          description = None,
          multiple = false,
          typeInfo = albumAssetReferenceTypeInfo,
          conditions = Map.empty
        )
      )
    ),
    "load-album-id"
  )

  private val saveAlbumOperator = WithId(
    PipelineOperator(
      name = "Save album",
      description = None,
      category = Some("OTHER"),
      className = "SaveAlbum",
      moduleName = "deepcortex.pipelines.operators.save_album",
      packageId = "deepcortex-package-id",
      inputs = Seq(
        PipelineOperatorInput(
          name = "album",
          description = None,
          `type` = albumLibType,
          covariate = true,
          required = true
        )
      ),
      outputs = Seq(
        PipelineOperatorOutput(
          description = None,
          `type` = PrimitiveDataType.String
        )
      ),
      params = Seq(
        OperatorParameter(
          name = "name",
          description = None,
          multiple = false,
          typeInfo = StringParameterTypeInfo(Seq.empty, Seq.empty),
          conditions = Map.empty
        ),
        OperatorParameter(
          name = "description",
          description = None,
          multiple = false,
          typeInfo = StringParameterTypeInfo(Seq.empty, Seq.empty),
          conditions = Map.empty
        ),
        OperatorParameter(
          name = "album_size",
          description = None,
          multiple = false,
          typeInfo = StringParameterTypeInfo(Seq("small", "big"), Seq.empty),
          conditions = Map.empty
        )
      )
    ),
    "save-album-id"
  )

  private val intGeneratorOperator = WithId(
    PipelineOperator(
      name = "Int generator",
      description = None,
      category = Some("OTHER"),
      className = "Generator",
      moduleName = "deepcortex.pipelines.operators.int_generator",
      packageId = "deepcortex-package-id",
      inputs = Seq.empty,
      outputs = Seq(
        PipelineOperatorOutput(
          description = None,
          `type` = PrimitiveDataType.Integer
        )
      ),
      params = Seq.empty
    ),
    "int-generator-id"
  )

  private val dummyOperator = WithId(
    PipelineOperator(
      name = "dummy operator",
      description = None,
      category = Some("OTHER"),
      className = "DummyOperator",
      moduleName = "operators",
      packageId = "package-id",
      inputs = Seq(
        PipelineOperatorInput("x", None, PrimitiveDataType.Integer, true, true),
        PipelineOperatorInput("y", None, PrimitiveDataType.Integer, true, true),
        PipelineOperatorInput("z", None, PrimitiveDataType.Integer, true, true)
      ),
      outputs = Seq(
        PipelineOperatorOutput(None, PrimitiveDataType.Integer),
        PipelineOperatorOutput(None, PrimitiveDataType.Integer),
        PipelineOperatorOutput(None, PrimitiveDataType.Integer)
      ),
      params = Seq.empty
    ),
    "dummy-operator-id"
  )

  private val pipelineOperators = Seq(
    loadAlbumOperator,
    saveAlbumOperator,
    intGeneratorOperator,
    dummyOperator
  ).map(operator => operator.id -> operator).toMap

  private val loadAlbumStep = PipelineStep(
    id = "load-album-step",
    operatorId = loadAlbumOperator.id,
    inputs = Map.empty,
    params = Map(
      "album_id" -> PipelineParams.StringParam("album-123")
    ),
    coordinates = None
  )

  private val loadAlbumStepOutputReference = PipelineOutputReference(
    stepId = loadAlbumStep.id,
    outputIndex = 0
  )
  private val saveAlbumStep = PipelineStep(
    id = "save-album-step",
    operatorId = saveAlbumOperator.id,
    inputs = Map(
      "album" -> loadAlbumStepOutputReference
    ),
    params = Map.empty,
    coordinates = None
  )

  private def dummyStep(
    id: String,
    inputs: Map[String, PipelineOutputReference],
    operatorId: String = dummyOperator.id,
    params: PipelineParams = Map.empty,
    coordinates: Option[PipelineCoordinates] = None
  ): PipelineStep =
    PipelineStep(
      id = id,
      operatorId = operatorId,
      inputs = inputs,
      params = params,
      coordinates = coordinates
    )


  "PipelineValidator#validatePipelineSteps" should {

    "return unit when steps are valid" in {

      val testCases = Table(
        "steps",
        Seq(
          loadAlbumStep
        ),
        Seq(
          loadAlbumStep,
          saveAlbumStep
        )
      )

      forAll(testCases) { (steps) =>
        PipelineValidator.validatePipelineSteps(steps, pipelineOperators) shouldBe ().asRight
      }
    }

    "return error when step ids not unique" in {
      PipelineValidator.validatePipelineSteps(
        Seq(loadAlbumStep, saveAlbumStep.copy(id = loadAlbumStep.id)),
        pipelineOperators
      ) shouldBe PipelineValidationError.StepsIdsAreNotUnique.asLeft
    }

    "return error when step operator not found" in {
      PipelineValidator.validatePipelineSteps(
        Seq(loadAlbumStep),
        Map.empty
      ) shouldBe PipelineValidationError.OperatorNotFound(loadAlbumOperator.id).asLeft
    }

    "return error when invalid output reference is provided in step" in {
      val invalidInputStep = saveAlbumStep.copy(
        inputs = Map(
          "album" -> PipelineOutputReference(
            loadAlbumStep.id,
            3
          )
        )
      )

      PipelineValidator.validatePipelineSteps(
        Seq(loadAlbumStep, invalidInputStep),
        pipelineOperators
      ) shouldBe PipelineValidationError.InvalidOutputReference(loadAlbumOperator.id, "Load album", 3).asLeft
    }

    "return error when invalid parameter is provided in step" in {
      val invalidParam = PipelineParams.IntParam(123)
      val invalidInputStep = loadAlbumStep.copy(
        params = Map(
          "album_id" -> invalidParam
        )
      )

      val result = PipelineValidator.validatePipelineSteps(
        Seq(invalidInputStep),
        pipelineOperators
      )

      result.isLeft shouldBe true
      result.left.get shouldBe a[PipelineValidationError.InvalidParamValue]
    }

    "return error when invalid parameter value is provided in step" in {
      val invalidParam = PipelineParams.StringParam("???")
      val invalidInputStep = loadAlbumStep.copy(
        params = Map(
          "album_size" -> invalidParam
        )
      )

      val result = PipelineValidator.validatePipelineSteps(
        Seq(invalidInputStep),
        pipelineOperators
      )

      result.isLeft shouldBe true
      result.left.get shouldBe a[PipelineValidationError.PipelineParamNotFound]
    }

    "return error when invalid input name is provided in step" in {
      val invalidInputName = "invalidInputName"
      val invalidInputStep = saveAlbumStep.copy(
        inputs = Map(
          invalidInputName -> loadAlbumStepOutputReference
        )
      )

      PipelineValidator.validatePipelineSteps(
        Seq(invalidInputStep),
        pipelineOperators
      ) shouldBe InvalidInput(saveAlbumOperator.id, "Save album", invalidInputName).asLeft
    }

    "return error when incompatible data type is provided as input for step" in {
      val stepFrom = PipelineStep(
        id = "generate-int-step",
        operatorId = intGeneratorOperator.id,
        inputs = Map.empty,
        params = Map.empty,
        coordinates = None
      )

      val stepTo = saveAlbumStep.copy(
        inputs = Map(
          "album" -> PipelineOutputReference(
            stepId = stepFrom.id,
            outputIndex = 0
          )
        )
      )

      PipelineValidator.validatePipelineSteps(
        Seq(stepFrom, stepTo),
        pipelineOperators
      ) shouldBe IncompatibleInput(albumLibType, PrimitiveDataType.Integer).asLeft
    }

    "return unit if there is no circular dependency between steps" in {
      val step1 = dummyStep(
        id = "step-1",
        inputs = Map.empty
      )
      val step2 = dummyStep(
        id = "step-2",
        inputs = Map(
          "y" -> PipelineOutputReference(step1.id, 1)
        )
      )
      val step3 = dummyStep(
        id = "step-3",
        inputs = Map(
          "x" -> PipelineOutputReference(step1.id, 2)
        )
      )
      val step4 = dummyStep(
        id = "step-4",
        inputs = Map(
          "x" -> PipelineOutputReference(step2.id, 2),
          "z" -> PipelineOutputReference(step3.id, 0)
        )
      )

      PipelineValidator.validatePipelineSteps(
        Seq(step1, step2, step3, step4),
        pipelineOperators
      ) shouldBe ().asRight
    }

    "return error there is circular dependency between steps" in {
      val step4Id = "step-4"
      val step1 = dummyStep(
        id = "step-1",
        inputs = Map.empty
      )
      val step2 = dummyStep(
        id = "step-2",
        inputs = Map(
          "x" -> PipelineOutputReference(step4Id, 0),
          "y" -> PipelineOutputReference(step1.id, 1)
        )
      )
      val step3 = dummyStep(
        id = "step-3",
        inputs = Map(
          "x" -> PipelineOutputReference(step2.id, 2)
        )
      )
      val step4 = dummyStep(
        id = step4Id,
        inputs = Map(
          "z" -> PipelineOutputReference(step3.id, 1)
        )
      )

      PipelineValidator.validatePipelineSteps(
        Seq(step1, step2, step3, step4),
        pipelineOperators
      ) shouldBe CircularDependency.asLeft
    }
  }

  "PipelineValidator#validatePipelineStepInfos" should {

    "return unit when steps are valid" in {

      val testCases = Table(
        "steps",
        Seq(
          PipelineStepInfo(loadAlbumStep, Map.empty)
        ),
        Seq(
          PipelineStepInfo(loadAlbumStep, Map("album_id" -> "Id of the input album")),
          PipelineStepInfo(saveAlbumStep, Map.empty)
        )
      )

      forAll(testCases) { (steps) =>
        PipelineValidator.validatePipelineStepInfos(steps, pipelineOperators) shouldBe ().asRight
      }
    }

    "return error when step ids not unique" in {
      PipelineValidator.validatePipelineStepInfos(
        Seq(
          PipelineStepInfo(loadAlbumStep, Map.empty),
          PipelineStepInfo(saveAlbumStep.copy(id = loadAlbumStep.id), Map.empty)
        ),
        pipelineOperators
      ) shouldBe PipelineValidationError.StepsIdsAreNotUnique.asLeft
    }

    "return error when first step is not valid" in {
      val unknownParamName = "unknown_param"
      val steps = Seq(
        PipelineStepInfo(loadAlbumStep, Map(unknownParamName -> "Unknown parameter")),
        PipelineStepInfo(saveAlbumStep, Map.empty)
      )

      val result = PipelineValidator.validatePipelineStepInfos(steps, pipelineOperators)
      result shouldBe Left(PipelineParamNotFound(unknownParamName))
    }
  }

  "PipelineValidator#dataTypeAreCompatible" should {

    // positive cases

    "return true when primitive types are compatible" in {
      val testCases = Table(
        ("from", "to"),

        (PrimitiveDataType.String, PrimitiveDataType.String),
        (PrimitiveDataType.Float, PrimitiveDataType.Float),
        (PrimitiveDataType.Boolean, PrimitiveDataType.Boolean),
        (PrimitiveDataType.Integer, PrimitiveDataType.Integer)
      )

      forAll(testCases) { (from, to) =>
        PipelineValidator.dataTypesAreCompatible(from = from, to = to, covariate = true) shouldBe true
      }
    }

    "return true when complex types are compatible (no generic)" in {
      val testCases = Table(
        ("from", "to", "covariate"),

        (baseModelType, baseModelType, true),
        (albumApiType, baseModelType, true),
        (albumLibType, baseModelType, true),
        (tableLibType, tableApiType, true),

        (baseModelType, baseModelType, false),
        (tableLibType, tableLibType, false)
      )

      forAll(testCases) { (from, to, covariate) =>
        PipelineValidator.dataTypesAreCompatible(from = from, to = to, covariate = covariate) shouldBe true
      }
    }

    "return true when generic types are compatible" in {
      val testCases = Table(
        ("from", "to", "covariate"),

        (iterable(baseModelType), iterable(baseModelType), true),
        (iterable(albumLibType), iterable(baseModelType), true),

        (list(tableLibType), iterable(tableLibType), true),
        (list(baseModelType), iterable(baseModelType), true),
        (list(albumApiType), iterable(baseModelType), true),

        (tuple(albumLibType, tableLibType), tuple(baseModelType, baseModelType), true),
        (tuple(albumLibType, tableLibType), tuple(albumApiType, tableApiType), true),

        (list(tuple(albumLibType, tableLibType)), list(tuple(albumApiType, tableApiType)), true),
        (list(tuple(albumApiType, tableApiType)), list(tuple(albumApiType, tableApiType)), true),

        (list(list(albumLibType)), list(list(albumLibType)), false),
        (iterable(albumApiType), iterable(albumApiType), false),
        (list(tableLibType), list(tableLibType), false),
        (list(tuple(albumApiType, tableApiType)), list(tuple(albumApiType, tableApiType)), false),
        (list(list(albumLibType)), list(list(albumLibType)), false)
      )

      forAll(testCases) { (from, to, covariate) =>
        PipelineValidator.dataTypesAreCompatible(from = from, to = to, covariate = covariate) shouldBe true
      }
    }

    // negative cases

    "return false when primitive types are not compatible" in {
      val testCases = Table(
        ("from", "to"),

        (PrimitiveDataType.String, PrimitiveDataType.Float),
        (PrimitiveDataType.Boolean, PrimitiveDataType.Integer)
      )

      forAll(testCases) { (from, to) =>
        PipelineValidator.dataTypesAreCompatible(from = from, to = to, covariate = true) shouldBe false
      }
    }

    "return false when complex types are not compatible (no generic)" in {
      val testCases = Table(
        ("from", "to", "covariate"),

        (tableLibType, albumLibType, true),
        (albumLibType, tableLibType, true),
        (baseModelType, albumApiType, true),
        (baseModelType, albumLibType, true),
        (tableApiType, tableLibType, true),

        (tableLibType, tableApiType, false),
        (albumLibType, baseModelType, false)
      )

      forAll(testCases) { (from, to, covariate) =>
        PipelineValidator.dataTypesAreCompatible(from = from, to = to, covariate = covariate) shouldBe false
      }
    }

    "return false when generic types are not compatible" in {
      val testCases = Table(
        ("from", "to", "covariate"),

        (iterable(tableLibType), list(tableLibType), true),
        (iterable(baseModelType), list(baseModelType), true),

        (tuple(albumLibType, tableLibType), tuple(tableLibType, albumLibType), true),
        (tuple(baseModelType, baseModelType), tuple(baseModelType, baseModelType, baseModelType), true),

        (list(tuple(albumLibType, tableApiType)), list(tuple(albumApiType, tableLibType)), true),

        (iterable(albumLibType), iterable(baseModelType), false),
        (list(tableLibType), iterable(tableLibType), false),
        (list(albumApiType), iterable(baseModelType), false),

        (tuple(albumLibType, tableLibType), tuple(baseModelType, baseModelType), false),
        (tuple(albumLibType, tableLibType), tuple(albumLibType, baseModelType), false),
        (tuple(albumLibType, tableLibType), tuple(baseModelType, tableLibType), false),

        (list(tuple(albumLibType, tableLibType)), list(tuple(albumApiType, tableApiType)), false),

        (list(list(albumLibType)), iterable(iterable(baseModelType)), false),
        (list(list(albumLibType)), iterable(iterable(albumLibType)), false)
      )

      forAll(testCases) { (from, to, covariate) =>
        PipelineValidator.dataTypesAreCompatible(from = from, to = to, covariate = covariate) shouldBe false
      }
    }
  }


  "PipelineValidator#validateParams" should {

    def operatorParameter(
      name: String,
      typeInfo: ParameterTypeInfo,
      description: Option[String] = None,
      multiple: Boolean = false,
      conditions: Map[String, ParameterCondition] = Map.empty
    ): OperatorParameter =
      OperatorParameter(
        name = name,
        description = description,
        multiple = multiple,
        typeInfo = typeInfo,
        conditions = conditions
      )

    val param1 = "param1"
    val param2 = "param2"
    val param3 = "param3"
    val param4 = "param4"
    val param5 = "param5"

    val intTypeInfo = IntParameterTypeInfo(Seq.empty, Seq.empty, None, None, None)
    val intTypeInfoRange = intTypeInfo.copy(min = Some(5), max = Some(10))

    val floatTypeInfo = FloatParameterTypeInfo(Seq.empty, Seq.empty, None, None, None)
    val floatTypeInfoRange = floatTypeInfo.copy(min = Some(5), max = Some(10))

    "return unit if single params are valid" in {

      val testCases = Table(
        ("operatorParams", "stepParams"),

        (
          Seq(
            operatorParameter(param1, StringParameterTypeInfo(Seq.empty, Seq.empty)),
            operatorParameter(param2, StringParameterTypeInfo(Seq("val 1", "val 2"), Seq.empty))
          ),
          Map(
            param1 -> PipelineParams.StringParam("val"),
            param2 -> PipelineParams.StringParam("val 2")
          )
        ),

        (
          Seq(operatorParameter(param1, AssetParameterTypeInfo(AssetType.Table))),
          Map(param1 -> PipelineParams.StringParam("table-id"))
        ),

        (
          Seq(
            operatorParameter(param1, intTypeInfo),
            operatorParameter(param2, intTypeInfo.copy(values = Seq(10, 15)))
          ),
          Map(
            param1 -> PipelineParams.IntParam(123),
            param2 -> PipelineParams.IntParam(10)
          )
        ),

        (
          Seq(
            operatorParameter(param1, intTypeInfoRange),
            operatorParameter(param2, intTypeInfoRange),
            operatorParameter(param3, intTypeInfoRange),
            operatorParameter(param4, intTypeInfoRange.copy(min = None)),
            operatorParameter(param5, intTypeInfoRange.copy(max = None))
          ),
          Map(
            param1 -> PipelineParams.IntParam(5),
            param2 -> PipelineParams.IntParam(6),
            param3 -> PipelineParams.IntParam(10),
            param4 -> PipelineParams.IntParam(Int.MinValue),
            param5 -> PipelineParams.IntParam(Int.MaxValue)
          )
        ),

        (
          Seq(
            operatorParameter(param1, floatTypeInfo),
            operatorParameter(param2, floatTypeInfo.copy(values = Seq(10, 15))),
            operatorParameter(param3, floatTypeInfo)
          ),
          Map(
            param1 -> PipelineParams.FloatParam(123),
            param2 -> PipelineParams.FloatParam(10),
            param3 -> PipelineParams.IntParam(10)
          )
        ),

        (
          Seq(
            operatorParameter(param1, floatTypeInfoRange),
            operatorParameter(param2, floatTypeInfoRange),
            operatorParameter(param3, floatTypeInfoRange),
            operatorParameter(param4, floatTypeInfoRange.copy(min = None)),
            operatorParameter(param5, floatTypeInfoRange.copy(max = None))
          ),
          Map(
            param1 -> PipelineParams.FloatParam(5),
            param2 -> PipelineParams.FloatParam(6),
            param3 -> PipelineParams.FloatParam(10),
            param4 -> PipelineParams.FloatParam(Float.MinValue),
            param5 -> PipelineParams.FloatParam(Float.MaxValue)
          )
        ),

        (
          Seq(operatorParameter(param1, BooleanParameterTypeInfo(Seq.empty))),
          Map(param1 -> PipelineParams.BooleanParam(false))
        )
      )

      forAll(testCases) { (operatorParams, stepParams) =>
        PipelineValidator.validateParams(operatorParams, stepParams, Nil) shouldBe ().asRight
      }
    }

    "return unit if multiple params are valid" in {

      val testCases = Table(
        ("operatorParams", "stepParams"),

        (
          Seq(
            operatorParameter(param1, StringParameterTypeInfo(Seq.empty, Seq.empty), multiple = true),
            operatorParameter(param2, StringParameterTypeInfo(Seq("val 1", "val 2"), Seq.empty), multiple = true)
          ),
          Map(
            param1 -> PipelineParams.StringParams(Seq("val 1", "val 2")),
            param1 -> PipelineParams.StringParams(Seq("val 1", "val 2"))
          )
        ),

        (
          Seq(operatorParameter(param1, AssetParameterTypeInfo(AssetType.CvModel), multiple = true)),
          Map(param1 -> PipelineParams.StringParams(Seq("id 1", "id 2")))
        ),

        (
          Seq(
            operatorParameter(param1, intTypeInfo, multiple = true),
            operatorParameter(param2, intTypeInfo.copy(values = Seq(5, 10)), multiple = true),
            operatorParameter(param3, intTypeInfoRange, multiple = true)
          ),
          Map(
            param1 -> PipelineParams.IntParams(Seq(1, 3, 99)),
            param2 -> PipelineParams.IntParams(Seq(10, 5, 10)),
            param3 -> PipelineParams.IntParams(Seq(5, 7, 10, 8))
          )
        ),

        (
          Seq(
            operatorParameter(param1, floatTypeInfo, multiple = true),
            operatorParameter(param2, floatTypeInfo.copy(values = Seq(5, 10)), multiple = true),
            operatorParameter(param3, floatTypeInfoRange, multiple = true),
            operatorParameter(param4, floatTypeInfo, multiple = true)
          ),
          Map(
            param1 -> PipelineParams.FloatParams(Seq(1, 3, 99)),
            param2 -> PipelineParams.FloatParams(Seq(10, 5, 10)),
            param3 -> PipelineParams.FloatParams(Seq(5, 7, 10, 8)),
            param4 -> PipelineParams.IntParams(Seq(5, 7, 10, 8))
          )
        ),

        (
          Seq(operatorParameter(param1, BooleanParameterTypeInfo(Seq.empty), multiple = true)),
          Map(param1 -> PipelineParams.BooleanParams(Seq(true, false, false, true)))
        )
      )

      forAll(testCases) { (operatorParams, stepParams) =>
        PipelineValidator.validateParams(operatorParams, stepParams, Nil) shouldBe ().asRight
      }
    }

    "return error if single params are not valid" in {
      val testCases = Table(
        ("operatorParams", "stepParams"),

        (
          Seq(
            operatorParameter(param1, StringParameterTypeInfo(Seq("val 1", "val 2"), Seq.empty))
          ),
          Map(
            param1 -> PipelineParams.StringParam("val")
          )
        ),

        (
          Seq(
            operatorParameter(param1, intTypeInfo.copy(values = Seq(10, 15))),
            operatorParameter(param2, intTypeInfoRange)
          ),
          Map(
            param1 -> PipelineParams.IntParam(123),
            param2 -> PipelineParams.IntParam(123)
          )
        ),

        (
          Seq(
            operatorParameter(param1, floatTypeInfo.copy(values = Seq(10, 15))),
            operatorParameter(param2, floatTypeInfoRange)
          ),
          Map(
            param1 -> PipelineParams.FloatParam(123),
            param2 -> PipelineParams.FloatParam(123)
          )
        )
      )

      forAll(testCases) { (operatorParams, stepParams) =>
        val result = PipelineValidator.validateParams(operatorParams, stepParams, Nil)
        result.isLeft shouldBe true
        result.left.get shouldBe an[InvalidParamValue]
      }
    }

    "return error if multiple params are not valid" in {
      val testCases = Table(
        ("operatorParams", "stepParams"),

        (
          Seq(
            operatorParameter(param1, StringParameterTypeInfo(Seq("val 1", "val 2"), Seq.empty), multiple = true)
          ),
          Map(
            param1 -> PipelineParams.StringParam("val")
          )
        ),

        (
          Seq(
            operatorParameter(param1, intTypeInfo.copy(values = Seq(10, 15)), multiple = true),
            operatorParameter(param2, intTypeInfoRange, multiple = true)
          ),
          Map(
            param1 -> PipelineParams.IntParams(Seq(10, 123)),
            param2 -> PipelineParams.IntParams(Seq(7, 123))
          )
        ),

        (
          Seq(
            operatorParameter(param1, floatTypeInfo.copy(values = Seq(10, 15)), multiple = true),
            operatorParameter(param2, floatTypeInfoRange, multiple = true)
          ),
          Map(
            param1 -> PipelineParams.FloatParams(Seq(10, 123)),
            param2 -> PipelineParams.FloatParams(Seq(7, 123))
          )
        )
      )

      forAll(testCases) { (operatorParams, stepParams) =>
        val result = PipelineValidator.validateParams(operatorParams, stepParams, Nil)
        result.isLeft shouldBe true
        result.left.get shouldBe an[InvalidParamValue]
      }
    }

    "return error if pipeline parameter is not valid" in {
      val operatorParams = Seq(operatorParameter(param1, AssetParameterTypeInfo(AssetType.Table)))
      val stepParams = Map(param1 -> PipelineParams.StringParam("table-id"))

      val result = PipelineValidator.validateParams(operatorParams, stepParams, Seq("unknown-param"))
      result.isLeft shouldBe true
      result.left.get shouldBe a[PipelineParamNotFound]
    }

    // conditions
    "return unit if condition is satisfied (not multiple)" in {
      val testCases = Table(
        ("typeInfo", "condition", "value"),

        (
          StringParameterTypeInfo(Seq.empty, Seq.empty),
          StringParameterCondition(Seq("val 1", "val 2")),
          PipelineParams.StringParam("val 1")
        ),

        (
          intTypeInfo,
          IntParameterCondition(Seq(10, 15), None, None),
          PipelineParams.IntParam(15)
        ),

        (
          intTypeInfo,
          IntParameterCondition(Seq.empty, min = Some(5), max = Some(10)),
          PipelineParams.IntParam(7)
        ),

        (
          floatTypeInfo,
          FloatParameterCondition(Seq(10, 15), None, None),
          PipelineParams.FloatParam(15)
        ),

        (
          floatTypeInfo,
          FloatParameterCondition(Seq.empty, min = Some(5), max = Some(10)),
          PipelineParams.FloatParam(7)
        ),

        (
          BooleanParameterTypeInfo(Seq.empty),
          BooleanParameterCondition(false),
          PipelineParams.BooleanParam(false)
        )
      )

      forAll(testCases) { (typeInfo, condition, value) =>
        val operatorParams = Seq(
          operatorParameter(param1, intTypeInfo, conditions = Map(param2 -> condition)),
          operatorParameter(param2, typeInfo)
        )
        val stepParams = Map(
          param1 -> PipelineParams.IntParam(7),
          param2 -> value
        )

        PipelineValidator.validateParams(operatorParams, stepParams, Nil) shouldBe ().asRight
      }
    }

    "return unit if condition is satisfied (multiple)" in {
      val testCases = Table(
        ("typeInfo", "condition", "value"),

        (
          StringParameterTypeInfo(Seq.empty, Seq.empty),
          StringParameterCondition(Seq("val 1", "val 2")),
          PipelineParams.StringParams(Seq("val 2", "val 1"))
        ),

        (
          intTypeInfo,
          IntParameterCondition(Seq(10, 15), None, None),
          PipelineParams.IntParams(Seq(10, 15, 10))
        ),

        (
          intTypeInfo,
          IntParameterCondition(Seq.empty, min = Some(5), max = Some(10)),
          PipelineParams.IntParams(Seq(10, 6, 5, 8))
        ),

        (
          floatTypeInfo,
          FloatParameterCondition(Seq(10, 15), None, None),
          PipelineParams.FloatParams(Seq(10, 15, 10))
        ),

        (
          floatTypeInfo,
          FloatParameterCondition(Seq.empty, min = Some(5), max = Some(10)),
          PipelineParams.FloatParams(Seq(10, 6, 5, 8))
        ),

        (
          BooleanParameterTypeInfo(Seq.empty),
          BooleanParameterCondition(true),
          PipelineParams.BooleanParams(Seq(true, true))
        )
      )

      forAll(testCases) { (typeInfo, condition, value) =>
        val operatorParams = Seq(
          operatorParameter(param1, intTypeInfo, conditions = Map(param2 -> condition)),
          operatorParameter(param2, typeInfo, multiple = true)
        )
        val stepParams = Map(
          param1 -> PipelineParams.IntParam(7),
          param2 -> value
        )

        PipelineValidator.validateParams(operatorParams, stepParams, Nil) shouldBe ().asRight
      }
    }

    "return error if condition is not satisfied (not multiple)" in {
      val testCases = Table(
        ("typeInfo", "condition", "value"),

        (
          StringParameterTypeInfo(Seq.empty, Seq.empty),
          StringParameterCondition(Seq("val 1", "val 2")),
          PipelineParams.StringParam("val 3")
        ),

        (
          intTypeInfo,
          IntParameterCondition(Seq(10, 15), None, None),
          PipelineParams.IntParam(11)
        ),

        (
          intTypeInfo,
          IntParameterCondition(Seq.empty, min = Some(5), max = Some(10)),
          PipelineParams.IntParam(11)
        ),

        (
          floatTypeInfo,
          FloatParameterCondition(Seq(10, 15), None, None),
          PipelineParams.FloatParam(11)
        ),

        (
          floatTypeInfo,
          FloatParameterCondition(Seq.empty, min = Some(5), max = Some(10)),
          PipelineParams.FloatParam(11)
        ),

        (
          BooleanParameterTypeInfo(Seq.empty),
          BooleanParameterCondition(false),
          PipelineParams.BooleanParam(true)
        )
      )

      forAll(testCases) { (typeInfo, condition, value) =>
        val operatorParams = Seq(
          operatorParameter(param1, intTypeInfo, conditions = Map(param2 -> condition)),
          operatorParameter(param2, typeInfo)
        )
        val stepParams = Map(
          param1 -> PipelineParams.IntParam(7),
          param2 -> value
        )

        val result = PipelineValidator.validateParams(operatorParams, stepParams, Nil)
        result.isLeft shouldBe true
        result.left.get shouldBe a[ParamConditionNotSatisfied]
      }
    }

    "return error if condition is not satisfied (multiple)" in {
      val testCases = Table(
        ("typeInfo", "condition", "value"),

        (
          StringParameterTypeInfo(Seq.empty, Seq.empty),
          StringParameterCondition(Seq("val 1", "val 2")),
          PipelineParams.StringParams(Seq("val 1", "val 3"))
        ),

        (
          intTypeInfo,
          IntParameterCondition(Seq(10, 15), None, None),
          PipelineParams.IntParams(Seq(10, 12, 15))
        ),

        (
          intTypeInfo,
          IntParameterCondition(Seq.empty, min = Some(5), max = Some(10)),
          PipelineParams.IntParams(Seq(6, 12, 7))
        ),

        (
          floatTypeInfo,
          FloatParameterCondition(Seq(10, 15), None, None),
          PipelineParams.FloatParams(Seq(10, 12, 15))
        ),

        (
          floatTypeInfo,
          FloatParameterCondition(Seq.empty, min = Some(5), max = Some(10)),
          PipelineParams.FloatParams(Seq(6, 12, 7))
        ),

        (
          BooleanParameterTypeInfo(Seq.empty),
          BooleanParameterCondition(true),
          PipelineParams.BooleanParams(Seq(true, false, true))
        )
      )

      forAll(testCases) { (typeInfo, condition, value) =>
        val operatorParams = Seq(
          operatorParameter(param1, intTypeInfo, conditions = Map(param2 -> condition)),
          operatorParameter(param2, typeInfo, multiple = true)
        )
        val stepParams = Map(
          param1 -> PipelineParams.IntParam(7),
          param2 -> value
        )

        val result = PipelineValidator.validateParams(operatorParams, stepParams, Nil)
        result.isLeft shouldBe true
        result.left.get shouldBe a[ParamConditionNotSatisfied]
      }
    }

    "return error if condition target is not defined" in {
      val typeInfo = StringParameterTypeInfo(Seq.empty, Seq.empty)
      val condition = StringParameterCondition(Seq("val 1", "val 2"))

      val operatorParams = Seq(
        operatorParameter(param1, intTypeInfo, conditions = Map(param2 -> condition)),
        operatorParameter(param2, typeInfo)
      )
      val stepParams = Map(
        param1 -> PipelineParams.IntParam(7)
      )

      val result = PipelineValidator.validateParams(operatorParams, stepParams, Nil)
      result.isLeft shouldBe true
      result.left.get shouldBe a[ParamConditionNotSatisfied]
    }

    "return error if condition target is pipeline parameter and parameter is not defined" in {
      val operatorParams = Seq(
        operatorParameter(param1, StringParameterTypeInfo(Nil, Nil)),
        operatorParameter(param2, intTypeInfo, conditions = Map(param1 -> StringParameterCondition(Seq("foo"))))
      )
      val stepParams = Map(param1 -> PipelineParams.StringParam("bar")) // param2 is not defined
      val pipelineParameters = Seq(param1)

      val result = PipelineValidator.validateParams(operatorParams, stepParams, pipelineParameters)
      result.isLeft shouldBe true
      result.left.get shouldBe a[DerivedConditionalParameterMissing]
    }

    "return unit if condition target is pipeline parameter and parameter is not defined but also a pipeline param" in {
      val operatorParams = Seq(
        operatorParameter(param1, StringParameterTypeInfo(Nil, Nil)),
        operatorParameter(param2, intTypeInfo, conditions = Map(param1 -> StringParameterCondition(Seq("foo"))))
      )
      val stepParams = Map(param1 -> PipelineParams.StringParam("bar")) // param2 is not defined
      val pipelineParameters = Seq(param1, param2)

      val result = PipelineValidator.validateParams(operatorParams, stepParams, pipelineParameters)
      result shouldBe ().asRight
    }

    "return unit if condition target is pipeline parameter and all derived parameters defined" in {
      val operatorParams = Seq(
        operatorParameter(param1, StringParameterTypeInfo(Nil, Nil)),
        operatorParameter(param2, intTypeInfo, conditions = Map(param1 -> StringParameterCondition(Seq("foo")))),
        operatorParameter(param3, intTypeInfo, conditions = Map(param1 -> StringParameterCondition(Seq("bar"))))
      )
      val stepParams = Map(
        param1 -> PipelineParams.StringParam("bar"),
        param2 -> PipelineParams.IntParam(RandomGenerators.randomInt(100)),
        param3 -> PipelineParams.IntParam(RandomGenerators.randomInt(100))
      )
      val pipelineParameters = Seq(param1)

      val result = PipelineValidator.validateParams(operatorParams, stepParams, pipelineParameters)
      result shouldBe ().asRight
    }

  }


  private def list(elem: ComplexDataType) =
    ComplexDataType(
      definition = "List",
      parents = Seq(
        iterable(elem)
      ),
      typeArguments = Seq(elem)
    )

  private def iterable(elem: ComplexDataType) =
    ComplexDataType(
      definition = "Iterable",
      parents = Seq.empty,
      typeArguments = Seq(elem)
    )

  private def tuple(elems: ComplexDataType*) =
    ComplexDataType(
      definition = "Tuple",
      parents = Seq.empty,
      typeArguments = elems
    )
}

package cortex.api.job.project.`package`

object Sample {

  val projectPackageRequest = ProjectPackageRequest(
    projectFilesPath = "project/files/",
    name = "my-package",
    version = "V1.0",
    targetPrefix = "packages"
  )

  val cvTlModelPrimitives = List(
    CVTLModelPrimitive(
      name = "VGG17",
      description = Some("Silly example operator with params that don't make sense"),
      moduleName = "model.train",
      className = "VGG17Train",
      `type` = OperatorType.UTLP,
      params = List(
        OperatorParameter(
          name = "delta",
          multiple = false,
          description = Some("delta value"),
          conditions = Map.empty,
          typeInfo = OperatorParameter.TypeInfo.FloatInfo(FloatParameter(
            values = Seq.empty,
            default = Seq(42f),
            min = Some(11f)
          ))
        ),
        OperatorParameter(
          name = "mode",
          multiple = false,
          description = Some("train mode"),
          conditions = Map.empty,
          typeInfo = OperatorParameter.TypeInfo.StringInfo(StringParameter(
            values = Seq("strong, simple, weak, complex"),
            default = Seq("simple")
          ))
        ),
        OperatorParameter(
          name = "threshold",
          description = None,
          conditions = Map(
            "mode" -> ParameterCondition(ParameterCondition.Condition.StringCondition(StringParameterCondition(
              Seq("complex")
            )))
          )
        )
      ),
      isNeural = true
    )
  )

  val pipelineOperator = List(PipelineOperator(
    name = "abcd",
    className = "abcd",
    moduleName = "abcd",
    inputs = Seq(PipelineOperatorInput(
      name = "abcd",
      description = Some("description of operator"),
      `type` = Some(PipelineDataType(PipelineDataType.DataType.ComplexDataType(ComplexDataType(
        definition = "abcd",
        parents = Seq(ComplexDataType(
          definition = "xyz",
          parents = Seq(),
          typeArguments = Seq.empty[PipelineDataType]
        )),
        typeArguments = Seq.empty[PipelineDataType]
      )))),
      covariate = true,
      required = true
    )),
    outputs = Seq(PipelineOperatorOutput(
      description = Some("description of operator"),
      `type` = Some(PipelineDataType(PipelineDataType.DataType.PrimitiveDataType(PrimitiveDataType.Float)))
    )),
    params = Seq(OperatorParameter(
      name = "abcd",
      description = Some("description of operator"),
      multiple = true,
      typeInfo = OperatorParameter.TypeInfo.FloatInfo(FloatParameter(
        values = Seq.empty,
        default = Seq(42f),
        min = Some(11f),
        max = None,
        step = None
      )),
      conditions = Map.empty[String, ParameterCondition]
    ))
  ))

  val projectPackageResponse = ProjectPackageResponse(
    packageLocation = "packages/package-0.0.1.whl",
    cvTlModelPrimitives = cvTlModelPrimitives
  )

}

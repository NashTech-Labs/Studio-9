package cortex.rpc

import cortex.io.S3Client

class S3TaskRPC(bucket: String, override val basePath: String, s3client: S3Client)
  extends TaskRPC {

  override val rpcType: String = "s3-rpc"

  override def passParameters(taskPath: String, serializedParams: Array[Byte]): Unit = {
    s3client.put(bucket, buildParamEndpoint(taskPath), serializedParams)
  }

  override def getResults(taskPath: String): Array[Byte] = {
    s3client.get(bucket, buildResultEndpoint(taskPath))
  }

  override def getRpcDockerParams(taskPath: String): Seq[String] = {
    val params = {
      super.getRpcDockerParams(taskPath) ++
        Seq(
          s"--base-bucket=" + bucket,
          s"--rpc-param-endpoint=" + buildParamEndpoint(taskPath),
          s"--rpc-result-endpoint=" + buildResultEndpoint(taskPath)
        ) ++
          s3client.endpointUrl.fold(Seq.empty[String]) { endpoint =>
            Seq(s"--s3endpoint=$endpoint")
          } ++
          Seq(
            s"--s3access_key=${s3client.accessKey}",
            s"--s3secret_key=${s3client.secretKey}",
            s"--s3region=${s3client.region}"
          )
    }

    params
  }

  private[rpc] def buildParamEndpoint(taskPath: String) =
    s"$basePath/$taskPath/input.json"

  private[rpc] def buildResultEndpoint(taskPath: String) =
    s"$basePath/$taskPath/output.json"
}


package cortex.jobmaster.jobs.job.dataset

import cortex.io.S3Client
import cortex.common.Utils
import cortex.jobmaster.jobs.job.FileSource
import cortex.task.StorageAccessParams.S3AccessParams

case class TransferFile(filename: String, fileSizeInBytes: Long)

object TransferFileSource {

  case class S3FileSource(
      s3AccessParams: S3AccessParams,
      s3SourcePath:   Option[String]
  ) extends FileSource[TransferFile] {
    private lazy val s3Client = new S3Client(
      accessKey    = s3AccessParams.accessKey,
      secretKey    = s3AccessParams.secretKey,
      region       = s3AccessParams.region,
      sessionToken = s3AccessParams.sessionToken,
      endpointUrl  = s3AccessParams.endpointUrl
    )

    override def getFiles: Seq[TransferFile] = {
      s3Client.getFiles(s3AccessParams.bucket, s3SourcePath).map(s3File => {
        val filename = s3SourcePath.foldRight(s3File.filepath)(Utils.cutBasePath)
        TransferFile(
          filename        = filename,
          fileSizeInBytes = s3File.fileSizeInBytes
        )
      })
    }

    override def baseRelativePath: Option[String] = s3SourcePath
  }

}

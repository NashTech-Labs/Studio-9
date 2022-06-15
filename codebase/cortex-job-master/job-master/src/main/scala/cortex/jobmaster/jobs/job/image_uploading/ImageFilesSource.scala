package cortex.jobmaster.jobs.job.image_uploading

import cortex.io.S3Client
import cortex.common.Utils
import cortex.jobmaster.jobs.job.FileSource
import cortex.task.StorageAccessParams.S3AccessParams

case class ImageFile(filename: String, fileSizeInBytes: Long, referenceId: Option[String])

object ImageFilesSource {

  case class S3FilesSource(
      s3AccessParams: S3AccessParams,
      s3SourcePath:   Option[String]
  ) extends FileSource[ImageFile] {
    private lazy val s3Client = new S3Client(
      accessKey    = s3AccessParams.accessKey,
      secretKey    = s3AccessParams.secretKey,
      region       = s3AccessParams.region,
      sessionToken = s3AccessParams.sessionToken,
      endpointUrl  = s3AccessParams.endpointUrl
    )

    override def getFiles: Seq[ImageFile] = {
      s3Client.getFiles(s3AccessParams.bucket, s3SourcePath).map(s3File => {
        val filename = s3SourcePath.foldRight(s3File.filepath)(Utils.cutBasePath)
        ImageFile(
          filename        = filename,
          fileSizeInBytes = s3File.fileSizeInBytes,
          referenceId     = None
        )
      })
    }

    override def baseRelativePath: Option[String] = s3SourcePath
  }

  case class S3FilesSequence(imageFileSequence: Seq[ImageFile], basePathOption: Option[String] = None) extends FileSource[ImageFile] {
    override def getFiles: Seq[ImageFile] = {
      imageFileSequence
    }

    override def baseRelativePath: Option[String] = basePathOption
  }
}

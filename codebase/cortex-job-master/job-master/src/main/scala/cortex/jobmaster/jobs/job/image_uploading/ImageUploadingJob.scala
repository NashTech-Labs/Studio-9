package cortex.jobmaster.jobs.job.image_uploading

import com.github.tototoshi.csv.CSVReader
import cortex.CortexException
import cortex.common.future.FutureExtensions._
import cortex.common.logging.JMLoggerFactory
import cortex.common.{ Logging, Utils }
import cortex.io.S3Client
import cortex.jobmaster.jobs.TaskIdGenerator
import cortex.jobmaster.jobs.job.FileSource
import cortex.jobmaster.jobs.job.image_uploading.ImageUploadingJob._
import cortex.jobmaster.jobs.time.JobTimeInfo
import cortex.scheduler.TaskScheduler
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.image_uploading.ImageUploadingParams._
import cortex.task.image_uploading._
import cortex.task.task_creators.GenericTask

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

class ImageUploadingJob(
    scheduler:            TaskScheduler,
    imageUploadingModule: ImageUploadingModule,
    imageUploadingConfig: ImageUploadingConfig,
    outputS3AccessParams: S3AccessParams
)(implicit val context: ExecutionContext, val loggerFactory: JMLoggerFactory) extends TaskIdGenerator with Logging {

  /**
   * Supported img formats
   */
  val imgFormats = Seq(".png", ".jpg", ".jpeg")

  def uploadImages(jobId: String, params: ImageUploadingJobParams): Future[(ImageUploadingJobResults, JobTimeInfo.TasksTimeInfo)] = {
    def prepareTasks(baseRelativePath: Option[String], imageFiles: Seq[ImageFile], labels: Seq[(String, String)]) = {
      Try {
        val tasks = createTasks(
          jobId,
          params.albumPath,
          baseRelativePath,
          params.inS3AccessParams,
          imageFiles,
          labels,
          params.applyLogTransformations
        )
        val tasksSizes = tasks
          .map(x => s"{taskId: ${x.id}, " +
            s"num: ${x.getParams.images.size}, " +
            s"mem: ${x.memory}}")
          .mkString(",")
        log.info(s"Created tasks number: ${tasks.length}, task sizes: $tasksSizes")
        tasks
      }
    }

    val result: Future[(ImageUploadingJobResults, JobTimeInfo.TasksTimeInfo)] = for {
      s3OutClient <- Try(getS3Client(outputS3AccessParams)).toFuture
      (imageFiles, failedFiles) <- Try(prepareImageFiles(params.imageFilesSource)).toFuture
      labels <- Try(getLabels(params)).toFuture
      tasks <- prepareTasks(params.imageFilesSource.baseRelativePath, imageFiles, labels).toFuture
      finishedTasks <- Future.sequence(tasks.map(scheduler.submitTask))
    } yield {
      val actualFiles = s3OutClient.getFiles(
        bucket = outputS3AccessParams.bucket,
        path   = Some(params.albumPath)
      ).map(file => file.copy(filepath = Utils.cutBasePath(params.albumPath, file.filepath)))
      val flattenSucceed = finishedTasks.flatMap(_.succeed)
      val flattenFailed = finishedTasks.flatMap(_.failed)
      val tasksTimeInfo = finishedTasks.map(_.taskTimeInfo)
      val uploadedImages = flattenSucceed.map(x => {
        val newFileName = x.imagePath
        val fileSize = actualFiles.find(_.filepath == x.imagePath)
          .getOrElse(throw new CortexException(s"file size not found for image with path ${x.imagePath}"))
          .fileSizeInBytes
        val originalFile = imageFiles.find { img =>
          val fullPath = FileSource.getFullPath(params.imageFilesSource.baseRelativePath, img.filename)
          fullPath == x.originalPath
        }.getOrElse(throw new CortexException("can't find image file for original path"))

        //TODO use [[x.metadata]] when the system will be able to maintain image metadata transferring
        val metadata = Map[String, String]()
        UploadedImage(
          name        = originalFile.filename,
          labels      = x.labels,
          meta        = metadata,
          path        = newFileName,
          size        = fileSize,
          referenceId = originalFile.referenceId
        )
      })

      (ImageUploadingJobResults(
        succeed = uploadedImages,
        failed  = flattenFailed ++ failedFiles
      ), tasksTimeInfo)
    }

    result
  }

  def prepareImageFiles(imageFilesSource: FileSource[ImageFile]): (Seq[ImageFile], Seq[FailedImage]) = {
    val imageFiles: Seq[ImageFile] = imageFilesSource.getFiles
    //leaving only "well-sized" images images
    val (properFiles, filtered) = imageFiles.partition(file => {
      file.fileSizeInBytes > 0 && file.fileSizeInBytes <= imageUploadingConfig.imageMaxSize * 1024L * 1024L
    })
    val failedFiles = filtered.map(x => FailedImage(x.filename, s"${x.filename} was rejected because it's either empty or" +
      s" allocates more than ${imageUploadingConfig.imageMaxSize} megabytes"))
    (properFiles, failedFiles)
  }

  def calcGroupMemSize(imagesWithSize: Seq[(Long, LabeledImageRequest)]): Double = {
    imagesWithSize.map(_._1).sum / (1024L * 1024L).toDouble
  }

  /**
   *
   * @param imagesWithSize
   * @return
   */
  //TODO here we can also do minor optimization
  //split some task so that [abs(taskMemSize1 - taskMemSize2) -> Min]
  //http://www.geeksforgeeks.org/partition-a-set-into-two-subsets-such-that-the-difference-of-subset-sums-is-minimum
  def splitIfExceedMaxTaskMbSize(imagesWithSize: Seq[(Long, LabeledImageRequest)]): Seq[Seq[(Long, LabeledImageRequest)]] = {
    val memMegabytes: Double = calcGroupMemSize(imagesWithSize)
    if (imagesWithSize.size == 1) {
      Seq(imagesWithSize)
    } else if (imagesWithSize.isEmpty) {
      Seq()
    } else if (memMegabytes > imageUploadingConfig.maxTaskMemSize) {
      val center = imagesWithSize.size / 2
      val (left, right) = imagesWithSize.splitAt(center)
      (left.isEmpty, right.isEmpty) match {
        case (false, false) => splitIfExceedMaxTaskMbSize(left) ++ splitIfExceedMaxTaskMbSize(right)
        case (false, true)  => splitIfExceedMaxTaskMbSize(left)
        case (true, false)  => splitIfExceedMaxTaskMbSize(right)
        case (true, true)   => throw new Exception("Impossibru")
      }
    } else {
      Seq(imagesWithSize)
    }
  }

  /**
   * Splits input data between approximately [imageUploadingConfig.parallelizationFactor] tasks,
   * each task should contain at least [imageUploadingConfig.minGroupSize] items,
   * each task should take not more than [imageUploadingConfig.maxTaskMemSize]
   *
   * @param jobId
   * @param albumPath
   * @param inS3Credentials
   * @param imageFiles
   * @param labels
   * @return
   */
  def createTasks(
    jobId:                   String,
    albumPath:               String,
    baseRelativePath:        Option[String],
    inS3Credentials:         S3AccessParams,
    imageFiles:              Seq[ImageFile],
    labels:                  Seq[(String, String)],
    applyLogTransformations: Boolean
  ): Seq[GenericTask[S3ImportTaskResult, S3ImportTaskParams]] = {
    val groupedByType: Map[ProcessingType, Seq[(Long, LabeledImageRequest)]] = groupByType(imageFiles, labels, baseRelativePath)
    val groupSize = findGroupSize(imageFiles.size)

    val tasks = groupedByType.toSeq.flatMap {
      case (ptype, imagesWithSize) =>
        //1. create groups (this step is used to spread images across tasks despite on their actual size)
        val groups = imagesWithSize.grouped(groupSize).toList
        groups.flatMap { grouped =>
          //2. split concrete group to more whether its tasks still exceeds the [[maxTaskMemSize]] param
          val newGroups = splitIfExceedMaxTaskMbSize(grouped)
          newGroups.map(group => {
            val images = group.map(_._2)
            val params = createS3ImportParams(albumPath, inS3Credentials, images, ptype, applyLogTransformations)
            (calcGroupMemSize(group), params)
          })
        }
    }.zipWithIndex.map {
      case ((memMegabytes, params), index) =>
        val task = imageUploadingModule.transformTask(
          id       = genTaskId(jobId),
          jobId    = jobId,
          taskPath = s"$jobId/image_upload_$index",
          params   = params,
          cpus     = imageUploadingConfig.cpus,
          memory   = memMegabytes + imageUploadingConfig.additionalTaskSize
        )
        task.setAttempts(2)
        task
    }

    tasks
  }

  /**
   * Groups input s3files by type
   * for now only SAR, IMG type is available
   * to detect either SAR or IMG, see [[imgFormats]].
   * If a file endswith this format then
   * it will be considered as an image else as a SAR
   * @param imageFiles
   * @param labels
   * @return images with its size grouped by ProcessingType
   */
  def groupByType(
    imageFiles:       Seq[ImageFile],
    labels:           Seq[(String, String)],
    baseRelativePath: Option[String]
  ): Map[ProcessingType, Seq[(Long, LabeledImageRequest)]] = {
    val csvFiles = imageFiles.filter(_.filename.endsWith(".csv"))
    val probablyImages = imageFiles.filter(imagesFilter)
    val groupedByType: Map[ProcessingType, Seq[(Long, LabeledImageRequest)]] = probablyImages.map(f => {
      val curLabels = {
        labels.find { case (name, _) => f.filename == name }.map(_._2).toSeq
      }
      val processingType = {
        if (imgFormats.exists(f.filename.toLowerCase().endsWith)) {
          ProcessingType.IMG
        } else if (f.filename.toLowerCase().endsWith(".npy")) {
          ProcessingType.RMI
        } else {
          ProcessingType.SAR
        }
      }
      val metaPath = {
        if (processingType == ProcessingType.RMI) {
          val curNameWithoutFormat = f.filename.split(".npy", 2).headOption.getOrElse {
            log.error(s"can't extract nameWithoutFormat for ${f.filename}")
            throw new CortexException(s"can't extract nameWithoutFormat for ${f.filename}")
          }
          csvFiles.find(csv => {
            val nameWithoutFormat = csv.filename.split(".csv", 2).headOption.getOrElse {
              log.error(s"can't extract nameWithoutFormat for ${csv.filename}")
              throw new CortexException(s"can't extract nameWithoutFormat for ${csv.filename}")
            }
            curNameWithoutFormat == nameWithoutFormat
          }).map(x => FileSource.getFullPath(baseRelativePath, x.filename))
        } else {
          None
        }
      }
      (processingType, (f.fileSizeInBytes, LabeledImageRequest(
        imagePath     = FileSource.getFullPath(baseRelativePath, f.filename),
        defaultLabels = curLabels,
        metaPath      = metaPath
      )))
    }).groupBy(_._1).map {
      case (k, v) =>
        (k, v.map(_._2))
    }

    groupedByType
  }

  //TODO add another filters
  //consider invert filter statement (selecting only accepted formats)
  protected def imagesFilter: ImageFile => Boolean = {
    x: ImageFile => !x.filename.endsWith(".csv")
  }

  /**
   *
   * @param size initial group size
   * @param parallelizationFactor approximate amount of groups to create
   * @return
   */
  @scala.annotation.tailrec
  final def findGroupSize(size: Int, parallelizationFactor: Int = imageUploadingConfig.parallelizationFactor): Int = {
    if (parallelizationFactor <= 1) {
      size
    } else if (parallelizationFactor > size || size / parallelizationFactor < imageUploadingConfig.minGroupSize) {
      findGroupSize(size, parallelizationFactor - 1)
    } else {
      size / parallelizationFactor
    }
  }

  protected def createS3ImportParams(
    albumPath:               String,
    inS3Credentials:         S3AccessParams,
    inputImages:             Seq[LabeledImageRequest],
    processingType:          ProcessingType,
    applyLogTransformations: Boolean
  ): S3ImportTaskParams = {
    val s3ImportTaskParams = S3ImportTaskParams(
      albumPath               = albumPath,
      images                  = inputImages,
      inputS3Params           = inS3Credentials,
      processingType          = processingType,
      outputS3Params          = outputS3AccessParams,
      blockSize               = imageUploadingConfig.blockSize,
      applyLogTransformations = applyLogTransformations
    )
    s3ImportTaskParams
  }

  protected def getS3Client(s3AccessCredentials: S3AccessParams): S3Client = {
    val s3Client = new S3Client(
      accessKey    = s3AccessCredentials.accessKey,
      secretKey    = s3AccessCredentials.secretKey,
      sessionToken = s3AccessCredentials.sessionToken,
      region       = s3AccessCredentials.region,
      endpointUrl  = s3AccessCredentials.endpointUrl
    )
    s3Client
  }

  /**
   *
   * @return sequence of image name and it's label
   */
  def getLabels(params: ImageUploadingJobParams): Seq[(String, String)] = {
    def getLabelsInternal(content: Option[String]): Seq[(String, String)] = {
      content.fold[Seq[(String, String)]](Seq()) { internal =>
        val source = scala.io.Source.fromString(internal)
        //TODO should a header be ignored?
        CSVReader.open(source).all().filter(_.size >= 2).map(line => {
          val List(name, label) = line.take(2)
          (name, label)
        })
      }
    }

    val s3Client = getS3Client(params.inS3AccessParams)
    val part1 = params.csvFileS3Path.map(path => new String(s3Client.get(params.inS3AccessParams.bucket, path)))
    val part2 = params.csvFileBytes.map(new String(_))
    getLabelsInternal(part1) ++ getLabelsInternal(part2)
  }
}

object ImageUploadingJob {

  //TODO we should hide s3 params under some DAO object like it was done for [[image_processor.py]]
  //for now it is hard to do because it will require changing both scala/python part
  /**
   *
   * @param imageFilesSource source from which image files should be retrieved
   * @param csvFileS3Path path to csv labels file (either it or [[csvFileBytes]] will be defined)
   * @param csvFileBytes
   */
  case class ImageUploadingJobParams(
      albumPath:               String,
      inS3AccessParams:        S3AccessParams,
      imageFilesSource:        FileSource[ImageFile],
      csvFileS3Path:           Option[String],
      csvFileBytes:            Option[Array[Byte]],
      applyLogTransformations: Boolean
  )

  case class ImageUploadingJobResults(
      succeed: Seq[UploadedImage],
      failed:  Seq[FailedImage]
  )

  case class UploadedImage(
      name:        String,
      labels:      Seq[String],
      meta:        Map[String, String],
      path:        String,
      size:        Long,
      referenceId: Option[String]
  )
}

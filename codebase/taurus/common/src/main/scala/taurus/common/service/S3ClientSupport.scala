package taurus.common.service

import java.io.{ ByteArrayInputStream, InputStream }

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import awscala.BasicCredentialsProvider
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.model.{ ObjectMetadata, PutObjectResult, S3ObjectInputStream }
import com.amazonaws.services.s3.{ AmazonS3, AmazonS3ClientBuilder }
import com.typesafe.config.Config
import taurus.common.service.RemoteRepository.WriteResult

import scala.concurrent.{ ExecutionContext, Future }

trait S3Client extends RemoteRepository with Extension {

  protected[this] val amazonS3Client: AmazonS3
  protected[this] val bucketName: String

  protected[this] implicit val ec: ExecutionContext

  def write(path: String, content: Array[Byte]): Future[WriteResult] = {

    def createInputStream(content: Array[Byte]): Future[ByteArrayInputStream] = Future {
      new ByteArrayInputStream(content)
    }

    def writeFile(inputStream: ByteArrayInputStream): Future[PutObjectResult] = {
      Future {
        val meta = new ObjectMetadata()
        meta.setContentLength(content.length.toLong)
        amazonS3Client.putObject(bucketName, path, inputStream, meta)
      } andThen {
        case _ => inputStream.close // Close when done as a side effect
      }
    }

    val result: Future[WriteResult] =
      for {
        inputStream <- createInputStream(content)
        _ <- writeFile(inputStream)
      } yield WriteResult

    result

  }

  def read(path: String): Future[Array[Byte]] = {

    def createInputStream(path: String): Future[S3ObjectInputStream] = Future {
      amazonS3Client.getObject(bucketName, path).getObjectContent
    }

    def readFile(inputStream: InputStream): Future[Array[Byte]] = {
      Future {
        // NOTE: if below code does not perform well, try using:
        // org.apache.commons.io.IOUtils.toByteArray(inputStream)
        // Do not use Stream.continually(...) since it does memoization
        Iterator.continually(inputStream.read).takeWhile(_ != -1).map(_.toByte).toArray
      } andThen {
        case _ => inputStream.close() // Close when done as a side effect
      }
    }

    val result: Future[Array[Byte]] =
      for {
        inputStream <- createInputStream(path)
        result <- readFile(inputStream)
      } yield result

    result
  }

}

object S3Client extends ExtensionId[S3Client] with ExtensionIdProvider {

  override def createExtension(system: ExtendedActorSystem): S3Client = new S3Client {
    val settings = S3ClientSettings(system)

    override protected[this] val amazonS3Client: AmazonS3 = {
      val credentials = BasicCredentialsProvider(settings.accessKey, settings.secretKey)
      AmazonS3ClientBuilder.standard()
        .withCredentials(credentials)
        .withRegion(settings.region)
        .build
    }

    override protected[this] val bucketName = settings.bucketName

    // NOTE: using default dispatcher for now, if running out of threads use a dedicated ExecutionContext
    override protected[this] implicit val ec: ExecutionContext = system.dispatcher
  }

  override def lookup(): ExtensionId[_ <: Extension] = S3Client
}

// Actor support
trait S3ClientSupport extends RemoteRepositorySupport { self: Service =>

  val remoteRepository = RemoteRepository.s3Repository(context.system)

}

// Settings
class S3ClientSettings(config: Config) extends Extension {
  private val s3Config = config.getConfig("s3-client")

  val accessKey = s3Config.getString("access-key")
  val secretKey = s3Config.getString("secret-key")

  val region = Regions.fromName(s3Config.getString("region"))
  val bucketName = s3Config.getString("bucket-name")
}

object S3ClientSettings extends ExtensionId[S3ClientSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): S3ClientSettings = new S3ClientSettings(system.settings.config)

  override def lookup(): ExtensionId[_ <: Extension] = S3ClientSettings
}

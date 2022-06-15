package baile.services.remotestorage

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.ByteString
import baile.daocommons.sorting.SortBy
import baile.domain.remotestorage.TemporaryCredentials
import baile.domain.usermanagement.User

import scala.concurrent.{ ExecutionContext, Future }

// TODO Move this to cortex common repo and publish once for all the services to use
/**
 * Asynchronous object wrapper around some remote storage.
 * Used to write and read arbitrary data somewhere outside.
 */
trait RemoteStorageService {

  /**
   * Writes content in a binary form to the specified path.
   * @param content content to write.
   * @param path location to place content to.
   */
  def write(content: Array[Byte], path: String)(implicit ec: ExecutionContext): Future[File]

  /**
   * Writes content in a binary form to the specified path.
   * @param source source for input stream to write.
   * @param path location to place content to.
   * @return info of the written file.
   */
  def write(
    source: Source[ByteString, Any],
    path: String
  )(implicit ec: ExecutionContext, materializer: Materializer): Future[File] =
    source.runWith(getSink(path))

  def doesExist(path: String)(implicit ec: ExecutionContext): Future[Boolean]

  def readMeta(path: String)(implicit ec: ExecutionContext): Future[File]

  /**
   * Reads content in a binary form from the specified path.
   * @param path location of the content to read from.
   * @return content which was found (in case of success) under the specified path.
   */
  def read(path: String)(implicit ec: ExecutionContext): Future[Array[Byte]]

  def copy(sourcePath: String, targetPath: String)(implicit ec: ExecutionContext): Future[File]

  def move(sourcePath: String, targetPath: String)(implicit ec: ExecutionContext): Future[File]

  /**
   * Copies content to another storage
   * This one is really ineffective
   * @param sourceStorage storage to read from.
   * @param sourcePath location of the content to read from.
   * @param targetPath location of the content to write to.
   * @return
   */
  def copyFrom(
    sourceStorage: RemoteStorageService,
    sourcePath: String,
    targetPath: String
  )(implicit ec: ExecutionContext, materializer: Materializer): Future[File] =
    for {
      fromFile <- sourceStorage.streamFile(sourcePath)
      fromSource = fromFile.content
      resultFile <- write(fromSource, targetPath)
    } yield resultFile

  /**
   * Creates streamed file to read from the specified path.
   * @param path location of the content to read from.
   */
  def streamFile(path: String)(implicit ec: ExecutionContext): Future[StreamedFile]

  def streamFiles(path: String)(implicit ec: ExecutionContext): Future[Source[StreamedFile, NotUsed]]

  /**
   * Generate presigned URL for file with custom expiration time
   *
   * @param path path to the file
   * @param expiresIn seconds after passing of which URL will no longer be valid. Default is 7200 (two hours)
   * @return presigned URL for found file
   */
  def getExternalUrl(path: String, expiresIn: Long = 60 * 60 * 2): String

  /**
   * Join path sections
   * @param base  first segment
   * @param segments  more segments to join
   */
  def path(base: String, segments: String*): String

  /**
   * Splits the path into a list of segments
   *
   * @param path path
   * @return list of segments
   */
  def split(path: String): Seq[String]

  /**
   * Deletes content from the specified path.
   * @param path location to delete content from.
   */
  def delete(path: String)(implicit ec: ExecutionContext): Future[Unit]

  def getSink(path: String)(implicit ec: ExecutionContext): Sink[ByteString, Future[File]]

  def createDirectory(path: String)(implicit ec: ExecutionContext): Future[Directory]

  def doesDirectoryExist(path: String)(implicit ec: ExecutionContext): Future[Boolean]

  def moveDirectory(oldPath: String, newPath: String)(implicit ec: ExecutionContext): Future[Directory]

  def deleteDirectory(path: String)(implicit ec: ExecutionContext): Future[Unit]

  def listDirectory(path: String, recursive: Boolean)(implicit ec: ExecutionContext): Future[List[StoredObject]]

  def listFiles(
    path: String,
    page: Int,
    pageSize: Int,
    sortBy: Option[SortBy],
    search: Option[String],
    recursive: Boolean
  )(implicit ec: ExecutionContext): Future[(List[File], Int)]

  def getTemporaryCredentials(
    path: String,
    user: User
  )(implicit ec: ExecutionContext): Future[TemporaryCredentials]

}

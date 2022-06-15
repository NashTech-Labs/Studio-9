package orion.ipc

import scala.concurrent.{ ExecutionContext, Future }
import scala.language.reflectiveCalls
import scala.util.Try

// scalastyle:off structural.type
package object common {

  /**
   * Using block to implement Automatic Resource Management
   *
   * @param resource A Resource
   * @param block    Block to execute
   * @tparam T Closable type
   * @return
   * Example:
   * using(new Closable()) {
   * closable => closable.doSomething()
   * }
   */
  final def using[T <: { def close(): Unit }](resource: T)(block: T => Unit): Unit = {
    Try(block(resource))
    Try(resource.close())
  }

  /*
    Returning T, throwing the exception on failure
    Example:
    val zkClient = new ZookeeperClient()
    val fromOffsets = Utils.retry(3) {
      zkClient.fetchOffsetFromKafka("consumer", topics)
    }
   */
  final def retry[T](n: Int = 3)(fn: => T): Try[T] = {
    Try {
      fn
    } recoverWith {
      case _ if n > 1 => retry(n - 1)(fn)
    }
  }

  /*
    Returning T, throwing the exception on failure
    Example
    val fromOffsets = Utils.retryWith(3)(new ZookeeperClient()) {
      zkClient => zkClient.fetchOffsetFromKafka("consumer", topics)
    }
   */
  final def retryWith[R <: { def close(): Unit }, T](n: Int = 3)(resource: R)(fn: R => T): Try[T] = {
    val result =
      Try {
        fn(resource)
      }.recoverWith {
        case _ if n > 1 => retryWith(n - 1)(resource)(fn)
      }
    Try(resource.close())
    result
  }

  def withRetry[T](retries: Int = 3)(f: => Future[T])(implicit ec: ExecutionContext): Future[T] =
    f.recoverWith {
      case _ if retries > 0 => withRetry(retries - 1)(f)
    }

  implicit class ExtendedFuture[+T](future: Future[T]) {
    def closeWhenDone[R <: { def close(): Unit }](resource: R)(implicit ec: ExecutionContext): Future[T] = {
      future.andThen {
        case _ => resource.close()
      }
    }
  }
}

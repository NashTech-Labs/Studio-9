package baile.utils

import scala.collection.Iterable
import scala.concurrent.{ ExecutionContext, Future }
import cats.implicits._

object CollectionProcessing {

  def handleIterableInParallelBatches[T](
    elements: Iterable[T],
    handler: Iterable[T] => Future[Unit],
    batchSize: Int,
    parallelismLevel: Int
  )(implicit ec: ExecutionContext): Future[Unit] = {
    elements.grouped(batchSize).grouped(parallelismLevel).toList.foldM[Future, Unit](()) { (_, chunk) =>
      Future.sequence(chunk.map(handler)).map(_ => ())
    }
  }
}

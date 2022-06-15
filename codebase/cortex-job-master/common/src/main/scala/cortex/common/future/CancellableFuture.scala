package cortex.common.future

import java.util.concurrent.{ Callable, FutureTask }

import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.Try

//implementation from here https://stackoverflow.com/questions/16009837/how-to-cancel-future-in-scala
class CancellableFuture[T](executionContext: ExecutionContext, block: => T) {
  private val promise = Promise[T]()

  def future: Future[T] = promise.future

  private val jf: FutureTask[T] = new FutureTask[T](
    new Callable[T] {
      override def call(): T = block
    }
  ) {
    override def done() = promise.complete(Try(get()))
  }

  def cancel(): Unit = {
    jf.cancel(true)
  }

  executionContext.execute(jf)
}

object CancellableFuture {
  def apply[T](todo: => T)(implicit executionContext: ExecutionContext): CancellableFuture[T] =
    new CancellableFuture[T](executionContext, todo)
}

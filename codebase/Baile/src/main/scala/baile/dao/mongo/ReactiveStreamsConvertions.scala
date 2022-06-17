package baile.dao.mongo

import java.util.concurrent.atomic.AtomicBoolean

import scala.language.implicitConversions

import org.mongodb.{ scala => mongoDB }
import org.{ reactivestreams => rxStreams }


trait ReactiveStreamsConvertions {

  implicit def observableToPublisher[T](observable: mongoDB.Observable[T]): rxStreams.Publisher[T] =
    ObservableToPublisher(observable)

  case class ObservableToPublisher[T](observable: mongoDB.Observable[T]) extends rxStreams.Publisher[T] {
    def subscribe(subscriber: rxStreams.Subscriber[_ >: T]): Unit = {
      observable.subscribe(
        new mongoDB.Observer[T]() {
          override def onSubscribe(subscription: mongoDB.Subscription): Unit = {
            subscriber.onSubscribe(new rxStreams.Subscription() {
              private final val cancelled: AtomicBoolean = new AtomicBoolean

              def request(n: Long): Unit = {
                if (!subscription.isUnsubscribed && n < 1) {
                  subscriber.onError(new IllegalArgumentException(
                    """3.9 While the Subscription is not cancelled,
                      |Subscription.request(long n) MUST throw a java.lang.IllegalArgumentException if the
                      |argument is <= 0.""".stripMargin
                  ))
                } else {
                  subscription.request(n)
                }
              }

              def cancel(): Unit = {
                if (!cancelled.getAndSet(true)) subscription.unsubscribe()
              }
            })
          }

          def onNext(result: T): Unit = subscriber.onNext(result)

          def onError(e: Throwable): Unit = subscriber.onError(e)

          def onComplete(): Unit = subscriber.onComplete()
        }
      )
    }
  }

}

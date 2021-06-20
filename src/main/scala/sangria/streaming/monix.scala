package sangria.streaming

import scala.language.higherKinds
import _root_.monix.execution.Scheduler
import _root_.monix.reactive._
import _root_.monix.eval.Task
import _root_.monix.execution.CancelableFuture
import cats.effect.ExitCase

import scala.concurrent.Future

object monix {
  class ObservableSubscriptionStream(implicit scheduler: Scheduler)
      extends SubscriptionStream[Observable] {
    def supported[T[_]](other: SubscriptionStream[T]): Boolean =
      other.isInstanceOf[ObservableSubscriptionStream]

    def map[A, B](source: Observable[A])(fn: A => B): Observable[B] = source.map(fn)

    def singleFuture[T](value: Future[T]): Observable[T] =
      Observable.fromFuture(value)

    def single[T](value: T): Observable[T] = Observable(value)

    def mapFuture[A, B](source: Observable[A])(fn: A => Future[B]): Observable[B] =
      source.mergeMap(a => Observable.fromFuture(fn(a)))

    def first[T](s: Observable[T]): CancelableFuture[T] =
      s.firstOrElseL(
        throw new IllegalStateException(
          "Promise was not completed - observable haven't produced any elements."))
        .runToFuture

    def failed[T](e: Throwable): Observable[Nothing] = Observable.raiseError(e)

    def onComplete[Ctx, Res](result: Observable[Res])(op: => Unit): Observable[Res] =
      result.guaranteeCase {
        case ExitCase.Error(e) => Task(op)
        case _ => Task(op)
      }

    def flatMapFuture[Ctx, Res, T](future: Future[T])(
        resultFn: T => Observable[Res]): Observable[Res] =
      Observable.fromFuture(future).mergeMap(resultFn)

    def merge[T](streams: Vector[Observable[T]]): Observable[T] =
      if (streams.nonEmpty) {
        Observable.merge(streams: _*)
      } else
        throw new IllegalStateException("No streams produced!")

    def recover[T](stream: Observable[T])(fn: Throwable => T): Observable[T] =
      stream.onErrorRecover { case e => fn(e) }
  }

  implicit def observableSubscriptionStream(implicit
      scheduler: Scheduler): SubscriptionStream[Observable] =
    new ObservableSubscriptionStream
}

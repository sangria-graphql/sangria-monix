package sangria.streaming

import java.util.concurrent.atomic.AtomicInteger

import language.postfixOps
import _root_.monix.execution.Scheduler.Implicits.global
import _root_.monix.reactive.Observable
import _root_.monix.reactive.subjects.PublishSubject

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class MonixIntegrationSpec extends AnyWordSpec with Matchers {
  val impl: SubscriptionStream[Observable] = new monix.ObservableSubscriptionStream

  "Monix Integration" should {
    "support itself" in {
      impl.supported(monix.observableSubscriptionStream) should be(true)
    }

    "map" in {
      res(impl.map(Observable(1, 2, 10))(_ + 1)) should be(List(2, 3, 11))
    }

    "singleFuture" in {
      res(impl.singleFuture(Future.successful("foo"))) should be(List("foo"))
    }

    "single" in {
      res(impl.single("foo")) should be(List("foo"))
    }

    "mapFuture" in {
      res(impl.mapFuture(Observable(1, 2, 10))(x ⇒ Future.successful(x + 1))) should be(
        List(2, 3, 11))
    }

    "first" in {
      res(impl.first(Observable(1, 2, 3))) should be(1)
    }

    "first throws error on empty" in {
      an[IllegalStateException] should be thrownBy res(impl.first(Observable()))
    }

    "failed" in {
      an[IllegalStateException] should be thrownBy res(
        impl.failed(new IllegalStateException("foo")))
    }

    "onComplete handles success" in {
      val subj = PublishSubject[Int]()
      val count = new AtomicInteger(0)
      def inc() = count.getAndIncrement()

      val updated = impl.onComplete(subj)(inc())

      subj.onNext(1)
      subj.onNext(2)
      subj.onComplete()

      Await.ready(updated.toListL.runToFuture, 2 seconds)

      count.get() should be(1)
    }

    "onComplete handles failure" in {
      val subj = PublishSubject[Int]()
      val count = new AtomicInteger(0)
      def inc() = count.getAndIncrement()

      val updated = impl.onComplete(subj)(inc())

      subj.onError(new IllegalStateException("foo"))

      Await.ready(updated.toListL.runToFuture, 2 seconds)

      count.get() should be(1)
    }

    "flatMapFuture" in {
      res(
        impl.flatMapFuture(Future.successful(1))(i ⇒
          Observable(i.toString, (i + 1).toString))) should be(List("1", "2"))
    }

    "recover" in {
      val obs = Observable(1, 2, 3, 4).map { i ⇒
        if (i == 3) throw new IllegalStateException("foo")
        else i
      }

      res(impl.recover(obs)(_ ⇒ 100)) should be(List(1, 2, 100))
    }

    "merge" in {
      val obs1 = Observable(1, 2)
      val obs2 = Observable(3, 4)
      val obs3 = Observable(100, 200)

      val result = res(impl.merge(Vector(obs1, obs2, obs3)))

      result should (have(size(6))
        .and(contain(1))
        .and(contain(2))
        .and(contain(3))
        .and(contain(4))
        .and(contain(100))
        .and(contain(200)))
    }

    "merge 2" in {
      val obs1 = Observable(1, 2)
      val obs2 = Observable(100, 200)

      val result = res(impl.merge(Vector(obs1, obs2)))

      result should (have(size(4))
        .and(contain(1))
        .and(contain(2))
        .and(contain(100))
        .and(contain(200)))
    }

    "merge 1" in {
      val obs1 = Observable(1, 2)

      val result = res(impl.merge(Vector(obs1)))

      result should (have(size(2)).and(contain(1)).and(contain(2)))
    }

    "merge throws exception on empty" in {
      an[IllegalStateException] should be thrownBy impl.merge(Vector.empty)
    }
  }

  def res[T](obs: Observable[T]) =
    Await.result(obs.toListL.runToFuture, 2 seconds)

  def res[T](f: Future[T]) =
    Await.result(f, 2 seconds)
}

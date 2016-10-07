package sangria.streaming

import language.postfixOps

import org.scalatest.{Matchers, WordSpec}

import _root_.monix.execution.Scheduler.Implicits.global
import _root_.monix.reactive.Observable

import scala.concurrent.Await
import scala.concurrent.duration._

class MonixIntegrationSpec extends WordSpec with Matchers {
  val impl: SubscriptionStream[Observable] = new monix.ObservableSubscriptionStream

  "Monix Integration" should {
    "support itself" in {
      impl.supported(new monix.ObservableSubscriptionStream) should be (true)
    }

    "map" in {
      res(impl.map(Observable(1, 2, 10))(_ + 1)) should be (List(2, 3, 11))
    }
  }

  def res[T](obs: Observable[T]) =
    Await.result(obs.toListL.runAsync, 2 seconds)
}

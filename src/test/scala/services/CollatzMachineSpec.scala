package services

import cats.effect._
import cats.effect.testing.scalatest.AsyncIOSpec
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.concurrent.duration._

class CollatzMachineSpec extends AsyncFreeSpec with Matchers with AsyncIOSpec {

  implicit val logger = Slf4jLogger.getLogger[IO]

  "CollatzMachine" - {
    "should get initial state correctly" in {
      val test = for {
        machine <- CollatzMachine.make[IO](1, 5, 1.millisecond)
        state <- machine.getStates
      } yield state

      test.asserting(_ shouldBe List(5))
    }

    "should update state correctly" in {
      val test = for {
        machine <- CollatzMachine.make[IO](1, 5, 1.millisecond)
        _ <- IO.sleep(100.millisecond)
        state <- machine.getStates
      } yield state.take(3)

      test.asserting(_ shouldBe List(5, 16, 8))
    }

    "should increment the last state correctly" in {
      val test = for {
        machine <- CollatzMachine.make[IO](1, 5, 100.millisecond)
        _ <- machine.increment(5)
        state <- machine.getStates
      } yield state.take(1)

      test.asserting(_ shouldBe List(10))
    }

    "should stop correctly" in {
      val test = for {
        machine <- CollatzMachine.make[IO](1, 5, 1.millisecond)
        _ <- IO.sleep(100.millisecond)  // wait for some updates
        _ <- machine.stop
        beforeStopStates <- machine.getStates
        _ <- IO.sleep(100.millisecond) // wait again to ensure no updates after stop
        afterStopStates <- machine.getStates
      } yield (beforeStopStates, afterStopStates)

      test.asserting {
        case (before, after) => before shouldBe after
      }
    }

    "should not have states after stop" in {
      val test = for {
        machine <- CollatzMachine.make[IO](1, 5, 1.millisecond)
        _ <- IO.sleep(100.millisecond) // wait for some updates
        _ <- machine.stop
        states <- machine.getStates
      } yield states

      test.asserting(_ shouldBe List())
    }
  }
}

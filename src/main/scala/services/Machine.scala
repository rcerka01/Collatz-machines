package services

import cats.effect._
import cats.effect.std.Queue
import cats.effect.syntax.all._
import cats.syntax.all._
import org.typelevel.log4cats.Logger

import scala.concurrent.duration._

trait CollatzMachine[F[_]] {
  def getStates: F[List[Int]]
  def stop: F[Unit]
  def increment(amount: Int): F[Unit]
}

private class LiveCollatzMachine[F[_]: Async: Logger](
    id: Int,
    startingNumber: Int,
    broadcastQueue: Queue[F, Int],
    timeout: Duration
)(implicit logger: Logger[F])
    extends CollatzMachine[F] {

  // Thread safe machines calculated state storage
  private val states = Ref.unsafe[F, List[Int]](List(startingNumber))

  // Sends Differed to wait for stop signal
  private val cancelSignal = Deferred.unsafe[F, Unit]

  // Collatz calculator
  private def getNewState(state: Int): Int =
    if (state % 2 == 0) state / 2
    else if (state != 1) 3 * state + 1
    else startingNumber

  // Add new value to Ref and broadcast
  private def updateStates(): F[Unit] = for {
    newState <- states.modify(msgs =>
      (msgs :+ getNewState(msgs.last), getNewState(msgs.last))
    )
    _ <- logger.info(s"New state for machine $id: $newState")
    _ <- broadcastQueue.offer(newState)
  } yield ()

  // Schedule for timeout (1 second)
  private val updateScheduler: F[Unit] =
    Temporal[F].race(
      // Updates until signal (race of both)
      Temporal[F].sleep(timeout) >> updateStates >> updateScheduler,
      cancelSignal.get
    ).void

  def start: F[Unit] = updateScheduler.start.void

  def stop: F[Unit] = for {
    _ <- logger.info(s"Stopping machine $id")
    _ <- cancelSignal.complete(()).void
    _ <- states.set(List())
  } yield ()

  def getStates: F[List[Int]] = states.get

  // Increment last and immediately broadcast
  def increment(amount: Int): F[Unit] = for {
    incrementedValue <- states.modify(msgs =>
      (msgs.init :+ (msgs.last + amount), msgs.last + amount)
    )
    _ <- logger.info(s"Incremented machine $id by $amount to $incrementedValue")
    _ <- broadcastQueue.offer(incrementedValue)
  } yield ()
}

object CollatzMachine {
  def make[F[_]: Async: Logger](
      id: Int,
      startingNumber: Int,
      timeout: Duration
  ): F[CollatzMachine[F]] = for {
    queue <- Queue.unbounded[F, Int] //Initialise queue
    machine = new LiveCollatzMachine[F](id, startingNumber, queue, timeout)
    _ <- machine.start
  } yield machine
}

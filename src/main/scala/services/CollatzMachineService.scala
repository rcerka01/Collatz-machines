package services

import cats.effect.implicits.genSpawnOps
import cats.effect.kernel.{Async, Concurrent}
import cats.implicits._
import config.AppConfig
import fs2.concurrent.Topic
import fs2.Stream
import org.typelevel.log4cats.Logger

import scala.collection.concurrent.TrieMap

trait CollatzMachineService[F[_]] {
  def createMachine(id: Int, startingNumber: Int): F[Unit]
  def destroyMachine(id: Int): F[Unit]
  def singleMachineUpdates(id: String): F[Stream[F, Int]]
  def allMachinesUpdates(): F[Stream[F, (Int, Int)]]
  def singleMachineIncrement(id: Int, amount: Int): F[Unit]
}

final private class CollatzMachineServiceLive[F[_]: Concurrent](config: AppConfig)(
  implicit F: Async[F], logger: Logger[F]) extends CollatzMachineService[F] {

  // Machines ledger, stores id with corresponding machine or state.
  private val machines: TrieMap[String, (CollatzMachine[F], Topic[F, Int])] = TrieMap.empty

  private def pollUpdates(machine: CollatzMachine[F], topic: Topic[F, Int], prevStates: List[Int]): F[Unit] = for {
    newStates <- machine.getStates // From machine Refs
    // List of states can grow large, this could be a bottleneck. If not needed old messages can be discarded
    diff = newStates.diff(prevStates)
    _ <- if (diff.nonEmpty) diff.traverse(topic.publish1) else F.unit
    _ <- F.sleep(config.service.poolingTimeout)
    _ <- pollUpdates(machine, topic, newStates) // Recursive. New states become prev.
  } yield ()

  def createMachine(id: Int, startingNumber: Int): F[Unit] = for {
    topic <- Topic[F, Int] // Multiple subscribers can listen to the values being pushed to the topic.
    _ <- topic.publish1(startingNumber)
    machine <- CollatzMachine.make[F](id, startingNumber, config.service.machineStateTimeout)
    _ <- if (machines.putIfAbsent(id.toString, (machine, topic)).isEmpty) { // Check for None, that means it is just added now
      pollUpdates(machine, topic, List(startingNumber)).start
    } else {
      F.raiseError[Unit](new Exception(s"Machine with ID $id already exists"))
    }
  } yield ()

  def allMachinesUpdates(): F[Stream[F, (Int, Int)]] = {
    val streams = machines.toList.flatMap {
      case (id, (_, topic)) =>
        // Subscribe all
        Some(topic.subscribe(config.service.maxQueued).map(n => (id.toInt, n)))
    }
    if (streams.isEmpty) {
      F.raiseError(new Exception("No machines available to connect"))
    } else {
      // Merge every machine stream
      F.pure(streams.reduceLeftOption(_.merge(_)).getOrElse(Stream.empty))
    }
  }

  def singleMachineUpdates(id: String): F[Stream[F, Int]] = {
    // Get by id, subscribe or error
    machines.get(id).fold(
      F.raiseError[Stream[F, Int]](new Exception(s"No machine found with ID: $id"))
    ) { case (_, topic) => F.pure(topic.subscribe(config.service.maxQueued)) }
  }

  def singleMachineIncrement(id: Int, amount: Int): F[Unit] = {
    // Gets machine and increments its state with its own method
    machines.get(id.toString).fold(
      F.raiseError[Unit](new Exception(s"No machine found with ID: $id"))
    )(_._1.increment(amount))
  }

  override def destroyMachine(id: Int): F[Unit] = {
    machines.get(id.toString) match {
      case Some((machine, _)) =>
        for {
          _ <- machine.stop // Machine class own method
          _ <- F.delay(machines.remove(id.toString))
          _ <- logger.info(s"Machine with ID $id destroyed successfully")
        } yield ()
      case None => F.raiseError[Unit](new Exception(s"No machine found with ID: $id"))
    }
  }
}

object CollatzMachineService {
  def make[F[_]: Async: Logger](appConfig: AppConfig): F[CollatzMachineService[F]] =
    Async[F].pure(new CollatzMachineServiceLive[F](appConfig))
}

package services

import cats.effect._
import cats.effect.testing.scalatest.AsyncIOSpec
import config.{ApiConfig, AppConfig, ServiceConfig}
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.concurrent.duration._

class CollatzMachineServiceSpec extends AsyncFreeSpec with Matchers with AsyncIOSpec {
  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  val testConfig: AppConfig = AppConfig(
    api = ApiConfig("", 0),
    service = ServiceConfig(500.millisecond, 500.millisecond, 10)
  )

  val service: CollatzMachineService[IO] = CollatzMachineService.make[IO](testConfig).unsafeRunSync()

  "CollatzMachineService" - {
    "should merge all machine updates" in {
      for {
        _ <- service.createMachine(15, 5)
        _ <- service.createMachine(16, 10)
        stream <- service.allMachinesUpdates()
        result <- stream.take(2).compile.toList
      } yield result.sorted shouldBe List((16, 5), (15, 16)).sorted
    }

    "should create a new machine and get updates" in {
      for {
        _ <- service.createMachine(1, 5)
        stream <- service.singleMachineUpdates("1")
        result <- stream.take(1).compile.toList
      } yield result shouldBe List(16)
    }

    "should increment machine value" in {
      for {
        _ <- service.createMachine(2, 5)
        _ <- service.singleMachineIncrement(2, 10)
        stream <- service.singleMachineUpdates("2")
        result <- stream.take(1).compile.toList
      } yield result shouldBe List(15) // assuming it should be 5 + 10
    }

    "should destroy machine" in {
      for {
        _ <- service.createMachine(3, 5)
        _ <- service.destroyMachine(3)
        result <- service.singleMachineUpdates("3").attempt
      } yield result match {
        case Left(e: Exception) => e.getMessage shouldBe "No machine found with ID: 3"
        case _ => fail("Expected a Left with specific exception message.")
      }
    }

    "should not create a machine with an existing ID" in {
      for {
        _ <- service.createMachine(4, 5)
        result <- service.createMachine(4, 10).attempt
      } yield result match {
        case Left(e: Exception) => e.getMessage shouldBe "Machine with ID 4 already exists"
        case _ => fail("Expected a Left with specific exception message.")
      }
    }

    "should not increment a non-existent machine" in {
      for {
        result <- service.singleMachineIncrement(999, 10).attempt
      } yield result match {
        case Left(e: Exception) => e.getMessage shouldBe "No machine found with ID: 999"
        case _ => fail("Expected a Left with specific exception message.")
      }
    }

    "should not destroy a non-existent machine" in {
      for {
        result <- service.destroyMachine(1000).attempt
      } yield result match {
        case Left(e: Exception) => e.getMessage shouldBe "No machine found with ID: 1000"
        case _ => fail("Expected a Left with specific exception message.")
      }
    }
  }
}

package api

import cats.effect._
import cats.effect.unsafe.implicits.global
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import org.http4s._
import org.http4s.circe.CirceSensitiveDataEntityDecoder.circeEntityDecoder
import org.http4s.implicits._
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito
import org.scalatest.Assertion
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import services.CollatzMachineService
import org.scalatestplus.mockito.MockitoSugar
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.concurrent.Future

class HttpApiSpec extends AsyncFreeSpec with MockitoSugar with Matchers {
  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  // A mock implementation of CollatzMachineService to use for testing
  val testService: CollatzMachineService[IO] = Mockito.mock(classOf[CollatzMachineService[IO]])
  Mockito.when(testService.createMachine(anyInt(), anyInt())).thenReturn(IO.pure(()))
  Mockito.when(testService.singleMachineIncrement(anyInt(), anyInt())).thenReturn(IO.pure(()))
  Mockito.when(testService.destroyMachine(anyInt())).thenReturn(IO.pure(()))

  val api: IO[HttpApi[IO]] = HttpApi.make[IO](testService)

  // Needed only in tests to decode expected
  implicit val decoderStatus: Decoder[ApiStatus] = deriveDecoder[ApiStatus]
  implicit val decoderError : Decoder[ApiError] = deriveDecoder[ApiError]

  // Helper to run requests
  def check[A](
                req: Request[IO],
                expectedStatus: Status,
                expectedBody: Option[A]
              )(implicit ed: EntityDecoder[IO, A]): Future[Assertion] = {
    val actualRespIO: IO[Response[IO]] = api.flatMap(_.routes.orNotFound(req))

    val actualResp = actualRespIO.unsafeToFuture()
    actualResp.map { resp =>
      resp.status shouldBe expectedStatus
      expectedBody.fold[Assertion](resp.body.compile.toVector.unsafeRunSync() shouldBe empty)(
        expected => resp.as[A].unsafeRunSync() shouldBe expected
      )
    }
  }

  "POST /create/{id}/{startingNumber}" - {
    "with valid parameters" in {
      val request = Request[IO](Method.POST, uri"/create/1/5")
      check(request, Status.Ok, Some(ApiStatus("success")))
    }

    "with invalid parameters" in {
      val request = Request[IO](Method.POST, uri"/create/notInt/5")
      check(request, Status.BadRequest, Some(ApiError("Invalid format for value: notInt. It must be an integer.")))
    }
  }

  "POST /increment/{id}/{amount}" - {
    "with valid parameters" in {
      val request = Request[IO](Method.POST, uri"/increment/1/2")
      check(request, Status.Ok, Some(ApiStatus("success")))
    }

    "with invalid id parameter" in {
      val request = Request[IO](Method.POST, uri"/increment/notInt/2")
      check(request, Status.BadRequest, Some(ApiError("Invalid format for value: notInt. It must be an integer.")))
    }

    "with invalid amount parameter" in {
      val request = Request[IO](Method.POST, uri"/increment/1/notInt")
      check(request, Status.BadRequest, Some(ApiError("Invalid format for value: notInt. It must be an integer.")))
    }
  }

  "POST /destroy/{id}" - {
    "with valid parameter" in {
      val request = Request[IO](Method.POST, uri"/destroy/1")
      check(request, Status.Ok, Some(ApiStatus("success")))
    }

    "with invalid parameter" in {
      val request = Request[IO](Method.POST, uri"/destroy/notInt")
      check(request, Status.BadRequest, Some(ApiError("Invalid format for value: notInt. It must be an integer.")))
    }
  }
}

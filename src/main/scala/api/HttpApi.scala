package api

import cats.effect.kernel.Async
import cats.implicits.{catsSyntaxApplicativeError, catsSyntaxTuple2Semigroupal}
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import cats.Monad
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder
import org.typelevel.log4cats.Logger
import services.CollatzMachineService
import scala.util.Try
import cats.syntax.flatMap._

case class ApiError(error: String) extends Throwable(error)
object ApiError {
  implicit val encoder: Encoder[ApiError] = deriveEncoder[ApiError]
}

case class ApiStatus(status: String)
object ApiStatus {
  implicit val encoder: Encoder[ApiStatus] = deriveEncoder[ApiStatus]
}

trait HttpApi[F[_]] {
  val routes: HttpRoutes[F]
}

final class HttpApiLive[F[_]: Async](ts: CollatzMachineService[F])
    extends HttpApi[F]
    with Http4sDsl[F] {

  // Helper to parse strings into integers safely
  private def parseInt(str: String): Either[String, Int] =
    Try(str.toInt).toEither.left.map { _ =>
      s"Invalid format for value: $str. It must be an integer."
    }

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {

    case POST -> Root / "create" / id / startingNumber =>
      (parseInt(id), parseInt(startingNumber)).tupled match {
        case Right((intId, intStartingNumber)) =>
          ts.createMachine(intId, intStartingNumber)
            .flatMap(_ => Ok(ApiStatus("success")))
            .handleErrorWith(e => BadRequest(ApiError(e.getMessage)))
        case Left(err) => BadRequest(ApiError(err))
      }

    case POST -> Root / "increment" / id / amount =>
      (parseInt(id), parseInt(amount)).tupled match {
        case Right((intId, intAmount)) =>
          ts.singleMachineIncrement(intId, intAmount)
            .flatMap(_ => Ok(ApiStatus("success")))
            .handleErrorWith(e => BadRequest(ApiError(e.getMessage)))
        case Left(err) => BadRequest(ApiError(err))
      }

    case POST -> Root / "destroy" / id =>
      parseInt(id) match {
        case Right(intId) =>
          ts.destroyMachine(intId)
            .flatMap(_ => Ok(ApiStatus("success")))
            .handleErrorWith(e => BadRequest(ApiError(e.getMessage)))
        case Left(err) => BadRequest(ApiError(err))
      }
  }
}

object HttpApi {
  def make[F[_]: Async: Monad: Logger](
      ts: CollatzMachineService[F]
  ): F[HttpApiLive[F]] =
    Async[F].pure(new HttpApiLive[F](ts))
}

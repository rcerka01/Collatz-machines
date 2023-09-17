package api

import cats.effect.kernel.Async
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import cats.Monad
import cats.implicits.catsSyntaxApplicativeError
import fs2.Stream
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import org.typelevel.log4cats.Logger
import services.CollatzMachineService

trait WsApi[F[_]] {
  def routes(ws: WebSocketBuilder2[F]): HttpRoutes[F]
}

final class WsApiLive[F[_]: Async](ts: CollatzMachineService[F])(implicit
    logger: Logger[F]
) extends WsApi[F]
    with Http4sDsl[F] {

  import cats.syntax.flatMap._

  def routes(ws: WebSocketBuilder2[F]): HttpRoutes[F] = HttpRoutes.of[F] {

    case GET -> Root / "messages" / id =>
      ts.singleMachineUpdates(id).flatMap { updatesStream =>
        val send = updatesStream
          .evalTap(num => logger.info(s"Sending number $num over WebSocket for machine $id"))
          .map(num => WebSocketFrame.Text(num.toString))
        ws.build(send, _ => Stream.empty)
      }.handleErrorWith { e =>
        val errorStream = Stream.emit(WebSocketFrame.Close(1002, s"Error: ${e.getMessage}")).covary[F].rethrow
        logger.error(e)(s"Error fetching updates for machine $id") >>
          ws.build(errorStream, _ => Stream.empty)
      }

    case GET -> Root / "messages" =>
      ts.allMachinesUpdates().flatMap { updatesStream =>
        val send = updatesStream
          .evalTap { case (id, num) => logger.info(s"Sending machine $id update: $num over WebSocket") }
          .map { case (id, num) => WebSocketFrame.Text(s"Machine $id: $num") }
        ws.build(send, _ => Stream.empty)
      }.handleErrorWith { e =>
        val errorStream = Stream.emit(WebSocketFrame.Close(1002, s"Error: ${e.getMessage}")).covary[F].rethrow
        logger.error(e)(s"Error fetching updates for all machines: ${e.getMessage}") >>
          ws.build(errorStream, _ => Stream.empty)
      }
  }
}

object WsApi {
  def make[F[_]: Async: Monad: Logger](
      ts: CollatzMachineService[F]
  ): F[WsApiLive[F]] =
    Async[F].pure(new WsApiLive[F](ts))
}

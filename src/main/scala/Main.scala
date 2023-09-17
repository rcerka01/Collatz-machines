import api.{HttpApi, WsApi}
import cats.effect._
import cats.syntax.semigroupk._
import config.AppConfig
import org.http4s.blaze.server.BlazeServerBuilder
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import services.CollatzMachineService

object Main extends IOApp.Simple {
  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  override def run: IO[Unit] =
    for {
      config <- AppConfig.load[IO]
      cm <- CollatzMachineService.make[IO](config)
      httpApi <- HttpApi.make[IO](cm)
      wsApi <- WsApi.make[IO](cm)
      _ <- BlazeServerBuilder[IO]
        .bindHttp(config.api.port, config.api.host)
        .withHttpWebSocketApp(wsb => (wsApi.routes(wsb) <+> httpApi.routes).orNotFound)
        .serve
        .compile
        .drain
    } yield ()
}

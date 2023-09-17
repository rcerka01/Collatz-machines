package config

import cats.effect.kernel.Sync
import pureconfig._
import pureconfig.generic.auto._

import scala.concurrent.duration.Duration

final case class AppConfig(
    api: ApiConfig,
    service: ServiceConfig
)

final case class ApiConfig(host: String, port: Int)

final case class ServiceConfig(machineStateTimeout: Duration, poolingTimeout: Duration, maxQueued: Int)

object AppConfig {
  def load[F[_]: Sync]: F[AppConfig] =
    Sync[F].blocking(ConfigSource.default.loadOrThrow[AppConfig])
}

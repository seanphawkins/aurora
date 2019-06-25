package aurora

import akka.actor.typed.Logger

import scala.concurrent.duration.FiniteDuration

sealed trait TTLConfig
object TTLConfig {
  case object INF extends TTLConfig
  final case class TTL(d: FiniteDuration) extends TTLConfig
}

final case class EntityConfig[C](ttl: TTLConfig = TTLConfig.INF, evictionThreshold: Long = Long.MaxValue, EvictionCoefficient: Float = 0.25f)(implicit val timeProvider: TimeProvider = SystemTimeProvider)

final case class EntityContext(timeProvider: TimeProvider, log: Logger)

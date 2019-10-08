package aurora

import java.time.Clock
import akka.actor.typed.Logger

import scala.concurrent.duration.FiniteDuration

sealed trait TTLConfig

object TTLConfig {
  case object INF extends TTLConfig
  final case class TTL(d: FiniteDuration) extends TTLConfig
}

final case class EntityConfig[C](ttl: TTLConfig = TTLConfig.INF, evictionThreshold: Long = Long.MaxValue, EvictionCoefficient: Float = 0.25f)(implicit val clock: Clock = Clock.systemUTC)

final case class EntityContext(clock: Clock, log: Logger)

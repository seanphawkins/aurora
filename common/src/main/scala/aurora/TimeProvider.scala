package aurora

import java.time.{Instant, LocalDate, ZoneId}
import java.util.concurrent.atomic.AtomicLong

import javax.naming.OperationNotSupportedException

trait TimeProvider {
  def now: Instant
  def localDate: LocalDate
  def millis: Long
}

object SystemTimeProvider extends TimeProvider {
  override def now: Instant = Instant.now
  override def localDate: LocalDate = LocalDate.now
  override def millis: Long = Instant.now.toEpochMilli
}

final case class FixedTimeProvider(now: Instant) extends TimeProvider {
  override def localDate: LocalDate = now.atZone(ZoneId.systemDefault()).toLocalDate
  override def millis: Long = now.toEpochMilli
}

final class IncrementingTimeProvider(start: Long = 0L) extends TimeProvider {
  private val current = new AtomicLong(start)

  override def now: Instant = Instant.ofEpochMilli(current.getAndIncrement())
  override def millis: Long = current.getAndIncrement()
  override def localDate: LocalDate = throw new OperationNotSupportedException("Incrementing time provider does not support localDate()")
}

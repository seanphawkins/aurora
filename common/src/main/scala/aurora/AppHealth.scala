package aurora

import java.time.Instant
import java.util.concurrent.atomic.{AtomicLong, AtomicReference}

import akka.actor.typed.Logger
import akka.event.Logging
import aurora.Severity.{ERROR, WARN}

import scala.annotation.tailrec

class IssueId(val value: Long) extends AnyVal
object IssueId {
  private val nextIssueId: AtomicLong = new AtomicLong(0)

  def next: IssueId = new IssueId(nextIssueId.getAndIncrement())
}

sealed trait Severity
object Severity {
  case object OK extends Severity { override def toString = "OK" }
  case object WARN extends Severity { override def toString = "WARN" }
  case object ERROR extends Severity { override def toString = "ERROR" }
  case object FATAL extends Severity { override def toString = "FATAL" }

  def apply(s: String): Severity = s.trim.toUpperCase match {
    case "OK" => OK
    case "WARN" => WARN
    case "ERROR" => ERROR
    case "FATAL" => FATAL
    case _ => FATAL
  }
}

case class Issue(id: IssueId, correlationId: String, severity: Severity, message: String, created: Instant, canBeCleared: () => Boolean)

object AppHealth {
  import Severity._

  private val issues: AtomicReference[Seq[Issue]] = new AtomicReference(Seq.empty)

  def createIssue(correlationId: String, severity: Severity, message: String, canBeCleared: () => Boolean = () => { false }): IssueId = {
    val newIssueId = IssueId.next
    val newIssue = Issue(newIssueId, correlationId, severity, message, Instant.now(), canBeCleared)
    getAndTransform[Seq[Issue]](issues, { _ :+ newIssue })
    newIssueId
  }

  def clear(id: IssueId): Unit = getAndTransform[Seq[Issue]](issues, { _.filterNot(_.id == id) })
  def clear(cid: String): Unit = getAndTransform[Seq[Issue]](issues, { _.filterNot(_.correlationId == cid) })

  def current: Severity = issues.get.map(_.severity).toSet match {
    case s1 if s1.contains(FATAL) => FATAL
    case s1 if s1.contains(ERROR) => ERROR
    case s1 if s1.contains(WARN) => WARN
    case _ => OK
  }

  def openIssues: Seq[Issue] = issues.get()

  @tailrec def getAndTransform[A](v: AtomicReference[A], transform: A => A): A = {
    val oldValue = v.get()
    val newValue = transform(oldValue)
    if (v.compareAndSet(oldValue, newValue)) oldValue else getAndTransform(v, transform)
  }
}

object AppHealthLogging {
  implicit class AHLogger(underlying: Logger) {
    def warningWithIssue(correlationId: String, pattern: String, args: Any*): Unit = {
      val aa = args.toArray
      underlying.log(Logging.WarningLevel, pattern, aa)
      AppHealth.createIssue(correlationId, WARN, replaceBraces(pattern, aa))
    }
    def errorWithIssue(correlationId: String, pattern: String, args: Any*): Unit = {
      val aa = args.toArray
      underlying.log(Logging.ErrorLevel, pattern, aa)
      AppHealth.createIssue(correlationId, ERROR, replaceBraces(pattern, aa))
    }

    private def replaceBraces(p: String, v: Array[Any]): String = {
      @tailrec def rb(p: String, v: Array[Any], acc: String): String =  (p, v) match {
        case (_,a) if a.length == 0 => acc + p
        case ("", _) => acc
        case (s , a) if s.startsWith("{}")=> rb(s.drop(2), a.tail, acc + a.head.toString)
        case (s, a) => rb(s.tail, a, acc + s.head)
      }
      if (v.length == 0) p else rb(p, v, "")
    }

  }
}
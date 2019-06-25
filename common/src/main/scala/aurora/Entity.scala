package aurora


import akka.actor.typed.ActorRef

trait Query[A] {
  def replyTo: ActorRef[A]
}

final case class EventReplyEnvelope[C](c: C, replyTo: ActorRef[Seq[Event]])

trait Event
trait Externalized { this: Event => }
trait Informational extends Externalized { this: Event => }
trait Narrative extends Externalized { this: Event => }
trait SnapshotTrigger { this: Event => }

case object CommandComplete extends Event with Informational

trait Entity[+A <: Entity[A, C, Q, E, R], C, Q <: Query[R], E <: Event, R] {
  def receive(ctx: EntityContext, cmd: C): Seq[E]
  def receiveQuery(ctx: EntityContext, q: Q): R
  def applyEvent(evt: E): A

  @inline protected def applying(events: E*): Seq[E] = Seq[E](events:_*)
  protected val NoOp = Seq.empty[E]
}

trait UntypedEntity[A <: Entity[A, Any, Query[Any], Event, Any]] extends Entity[A, Any, Query[Any], Event, Any]

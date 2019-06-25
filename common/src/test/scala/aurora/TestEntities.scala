package aurora

import akka.actor.typed.ActorRef

import scala.collection.mutable

sealed trait CounterCommand
case object Increment extends CounterCommand
case object Decrement extends CounterCommand

sealed trait CounterQuery extends Query[Long]
final case class QueryValue(replyTo: ActorRef[Long]) extends CounterQuery

sealed trait CounterEvent extends Event
case object Incremented extends CounterEvent with Externalized
case object Decremented extends CounterEvent with Externalized

final case class Counter(count: Long) extends Entity[Counter, CounterCommand, CounterQuery, CounterEvent, Long] {
  override def receive(ctx: EntityContext, cmd: CounterCommand): Seq[CounterEvent] = (ctx, cmd) match {
    case (_, Increment) => applying(Incremented)
    case (_, Decrement) => applying(Decremented)
  }

  override def receiveQuery(ctx: EntityContext, q: CounterQuery): Long = (ctx, q) match {
    case (_, QueryValue(_)) => count
  }

  override def applyEvent(evt: CounterEvent): Counter = evt match {
    case Incremented => copy(count = count + 1)
    case Decremented => copy(count = count - 1)
  }
}

class CounterPersistenceAdapter(seeds: (String, Long)*) extends PersistenceAdapter[Counter] {
  private val vMap = mutable.AnyRefMap[String, Long](seeds:_*)

  override def get(id: String): Option[Counter] = Option(Counter(vMap.getOrElse(id, 0L)))

  override def put(id: String, v: Counter): Unit = vMap.put(id, v.count)

  override def remove(id: String): Unit = vMap.remove(id)
}
package aurora

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, Props, Terminated, TypedActorContext}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityTypeKey}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import aurora.EntityWrapper.{ProcessCommand, ProcessQuery, Stop}

import scala.collection.mutable
import scala.reflect.ClassTag

private[aurora] sealed trait EntityWrapperCommand[C, Q <: Query[R], R]

private[aurora] object EntityWrapper {

  def transientEntityWrapper[A <: Entity[A, C, Q, E, R], C, Q <: Query[R], E <: Event, R](init: A)(implicit e1: ClassTag[A], e2: ClassTag[C], e3: ClassTag[Q], e4: ClassTag[E], e5: ClassTag[R], cfg: EntityConfig[C]): Behavior[EntityWrapperCommand[C, Q, R]] = {

    def tew(pc: EntityContext, init: A)(implicit e1: ClassTag[A], e2: ClassTag[C], e3: ClassTag[Q], e4: ClassTag[E], e5: ClassTag[R]): Behavior[EntityWrapperCommand[C, Q, R]] = {
      Behaviors.receive {
        case (_, ProcessCommand(c, ort)) =>
          val events = init.receive(pc, c)
          val ee = events.filter(_.isInstanceOf[Externalized])
          val ae = events.filterNot(_.isInstanceOf[Informational])
          ort foreach {_ ! ee :+ CommandComplete }
          tew(pc, ae.foldLeft(init) { case (state, ev) => state.applyEvent(ev) })
        case (_, ProcessQuery(q, rt)) =>
          rt ! init.receiveQuery(pc, q)
          Behavior.same
        case (_, Stop(_)) =>
          Behavior.stopped
        case (ctx, GenerateInterfaces(r)) =>
          val ch = ctx.messageAdapter[C]( m => ProcessCommand(m, None))
          val sch = ctx.messageAdapter[EventReplyEnvelope[C]]( m => ProcessCommand(m.c, Some(m.replyTo)))
          val qh = ctx.messageAdapter[Q](q => ProcessQuery(q, q.replyTo))
          r ! (ch, sch, qh)
          Behavior.same
      }
    }

    Behaviors.setup[EntityWrapperCommand[C, Q, R]] { ctx =>
      cfg.ttl match {
        case TTLConfig.INF =>
        case TTLConfig.TTL(d) => ctx.setReceiveTimeout(d, Stop(""))
      }
      val cf = EntityContext(cfg.clock, ctx.log)
      tew(cf,  init)
    }
  }

  def eventSourcedEntityWrapper[A <: Entity[A, C, Q, E, R], C, Q <: Query[R], E <: Event, R](init: A, name: String)(implicit e1: ClassTag[A], e2: ClassTag[C], e3: ClassTag[Q], e4: ClassTag[E], e5: ClassTag[R], cfg: EntityConfig[C]): Behavior[EntityWrapperCommand[C, Q, R]] = {
    Behaviors.setup[EntityWrapperCommand[C, Q, R]] { ctx =>
      cfg.ttl match {
        case TTLConfig.INF =>
        case TTLConfig.TTL(d) => ctx.setReceiveTimeout(d, Stop(name))
      }
      val cf = EntityContext(cfg.clock, ctx.log)
      EventSourcedBehavior[EntityWrapperCommand[C, Q, R], E, A](
        persistenceId = PersistenceId(name),
        emptyState = init,
        commandHandler =  {
          case (state, ProcessCommand(c, ort)) =>
            val events = state.receive(cf, c)
            val ee = events.filter(_.isInstanceOf[Externalized])
            val ae = collection.immutable.Seq(events.filterNot(_.isInstanceOf[Informational]):_*)
            ort foreach {_ ! ee :+ CommandComplete }
            if (ae.isEmpty) Effect.none else Effect.persist(ae)
          case (state, ProcessQuery(q, rt)) =>
            rt ! state.receiveQuery(cf, q)
            Effect.none
          case (_, Stop(_)) =>
            Effect.stop()
          case (_, GenerateInterfaces(r)) =>
            val ch = ctx.messageAdapter[C]( m => ProcessCommand(m, None))
            val sch = ctx.messageAdapter[EventReplyEnvelope[C]]( m => ProcessCommand(m.c, Some(m.replyTo)))
            val qh = ctx.messageAdapter[Q](q => ProcessQuery(q, q.replyTo))
            r ! (ch, sch, qh)
            Effect.none
        },
        eventHandler = {
          case (state: A, evt) => state.applyEvent(evt)
        }
      ).snapshotWhen((_, e, _) => e.isInstanceOf[SnapshotTrigger])
    }
  }

  def persistentEntityWrapper[A <: Entity[A, C, Q, E, R], C, Q <: Query[R], E <: Event, R](init: A, name: String)(implicit e1: ClassTag[A], e2: ClassTag[C], e3: ClassTag[Q], e4: ClassTag[E], e5: ClassTag[R], cfg: EntityConfig[C], pa: PersistenceAdapter[A]): Behavior[EntityWrapperCommand[C, Q, R]] = {

    def tew(pc: EntityContext, init: A, name: String)(implicit e1: ClassTag[A], e2: ClassTag[C], e3: ClassTag[Q], e4: ClassTag[E], e5: ClassTag[R], pa: PersistenceAdapter[A]): Behavior[EntityWrapperCommand[C, Q, R]] = {
      Behaviors.receive {
        case (_, ProcessCommand(c, ort)) =>
          val events = init.receive(pc, c)
          val ee = events.filter(_.isInstanceOf[Externalized])
          val ae = events.filterNot(_.isInstanceOf[Informational])
          ort foreach {_ ! ee :+ CommandComplete }
          val newState = ae.foldLeft(init) { case (state, ev) => state.applyEvent(ev) }
          if (ae.nonEmpty) pa.put(name, newState)
          tew(pc, newState, name)

        case (_, ProcessQuery(q, rt)) =>
          rt ! init.receiveQuery(pc, q)
          Behavior.same
        case (_, Stop(_)) =>
          Behavior.stopped
        case (ctx, GenerateInterfaces(r)) =>
          val ch = ctx.messageAdapter[C]( m => ProcessCommand(m, None))
          val sch = ctx.messageAdapter[EventReplyEnvelope[C]]( m => ProcessCommand(m.c, Some(m.replyTo)))
          val qh = ctx.messageAdapter[Q](q => ProcessQuery(q, q.replyTo))
          r ! (ch, sch, qh)
          Behavior.same
      }
    }

    Behaviors.setup[EntityWrapperCommand[C, Q, R]] { ctx =>
      cfg.ttl match {
        case TTLConfig.INF =>
        case TTLConfig.TTL(d) => ctx.setReceiveTimeout(d, Stop(""))
      }
      val cf = EntityContext(cfg.clock, ctx.log)
      tew(cf, pa.get(name).getOrElse(init), name)
    }
  }

  final case class ProcessCommand[C, Q <: Query[R], R](cmd: C, replyTo: Option[ActorRef[Seq[Event]]]) extends EntityWrapperCommand[C, Q, R]
  final case class ProcessQuery[C, Q <: Query[R], R](q: Q, replyTo: ActorRef[R]) extends EntityWrapperCommand[C, Q, R]
  final case class GenerateInterfaces[C, Q <: Query[R], R](replyTo: ActorRef[(ActorRef[C], ActorRef[EventReplyEnvelope[C]], ActorRef[Q])]) extends EntityWrapperCommand[C, Q, R]
  final case class Stop[C, Q <: Query[R], R](id: String) extends EntityWrapperCommand[C, Q, R]
}

sealed trait AggregateMessage[C, Q <: Query[R], R] { def id: String }
final case class AggregateCommand[C, Q <: Query[R], R](id: String, cmd: C) extends AggregateMessage[C, Q, R]
final case class AggregateStreamCommand[C, Q <: Query[R], R](id: String, cmd: EventReplyEnvelope[C]) extends AggregateMessage[C, Q, R]
final case class AggregateQuery[C, Q <: Query[R], R](id: String, q: Q) extends AggregateMessage[C, Q, R]
private[aurora] final case class GenerateAggregateInterfaces[C, Q <: Query[R], R](replyTo: ActorRef[(ActorRef[ShardingEnvelope[C]], ActorRef[ShardingEnvelope[EventReplyEnvelope[C]]], ActorRef[ShardingEnvelope[Q]])], id: String = "") extends AggregateMessage[C, Q, R]

object AggregateEntityFactory {
  def transient[A <: Entity[A, C, Q, E, R], C, Q <: Query[R], E <: Event, R](init: A, name: String)(implicit e1: ClassTag[A], e2: ClassTag[C], e3: ClassTag[Q], e4: ClassTag[E], e5: ClassTag[R], cfg: EntityConfig[C]): Behavior[EntityWrapperCommand[C, Q, R]] =
    EntityWrapper.transientEntityWrapper[A, C, Q, E, R](init)


  def eventSourced[A <: Entity[A, C, Q, E, R], C, Q <: Query[R], E <: Event, R](init: A, name: String)(implicit e1: ClassTag[A], e2: ClassTag[C], e3: ClassTag[Q], e4: ClassTag[E], e5: ClassTag[R], cfg: EntityConfig[C]): Behavior[EntityWrapperCommand[C, Q, R]] =
    EntityWrapper.eventSourcedEntityWrapper[A, C, Q, E, R](init, name)

  def persistent[A <: Entity[A, C, Q, E, R], C, Q <: Query[R], E <: Event, R](init: A, name: String)(implicit e1: ClassTag[A], e2: ClassTag[C], e3: ClassTag[Q], e4: ClassTag[E], e5: ClassTag[R], cfg: EntityConfig[C], pa: PersistenceAdapter[A]): Behavior[EntityWrapperCommand[C, Q, R]] =
    EntityWrapper.persistentEntityWrapper[A, C, Q, E, R](init, name)

}

private[aurora] object Aggregate {

  def localAggregate[A <: Entity[A, C, Q, E, R], C, Q <: Query[R], E <: Event, R](init: A, name: String, create: (A, String) => Behavior[EntityWrapperCommand[C, Q, R]])(implicit e1: ClassTag[A], e2: ClassTag[C], e3: ClassTag[Q], e4: ClassTag[E], e5: ClassTag[R], cfg: EntityConfig[C]): Behavior[AggregateMessage[C, Q, R]] = {

    def b(state: mutable.AnyRefMap[String, ActorRef[EntityWrapperCommand[C, Q, R]]], entityCount: Long, init: A, name: String, create: (A, String) => Behavior[EntityWrapperCommand[C, Q, R]])(implicit e1: ClassTag[A], e2: ClassTag[C], e3: ClassTag[Q], e4: ClassTag[E], e5: ClassTag[R], cfg: EntityConfig[C]): Behavior[AggregateMessage[C, Q, R]] = {

      def createChild(cx: TypedActorContext[AggregateMessage[C, Q, R]], iniy: A, id: String, create: (A, String) => Behavior[EntityWrapperCommand[C, Q, R]]): ActorRef[EntityWrapperCommand[C, Q, R]] = {
        val target = cx.asScala .spawn(create(init, id), id)
        cx.asScala.watch(target)
        target
      }

      def maybeGC(s: mutable.AnyRefMap[String, ActorRef[EntityWrapperCommand[C, Q, R]]], ec: Long, id: String, et: Long, ecoeff: Float): (Long, mutable.AnyRefMap[String, ActorRef[EntityWrapperCommand[C, Q, R]]]) = {
        val c = ec + (if (s.contains(id)) 0 else 1)
        if (c > et) {
          val numToDrop: Long = (et * ecoeff).toLong
          (Math.max(0L, c - numToDrop), s.drop(numToDrop.intValue()))
        } else
          (c, s)
      }

      Behaviors.receive[AggregateMessage[C, Q, R]] {
        case (ctx, AggregateCommand(id, cmd)) =>
          val (newEntityCount, newState) = maybeGC(state, entityCount, id, cfg.evictionThreshold, cfg.EvictionCoefficient)
          val target = newState.getOrElseUpdate(id, createChild(ctx, init, id, create))
          target ! ProcessCommand(cmd, None)
          b(newState, newEntityCount, init, name, create)
        case (ctx, AggregateStreamCommand(id, EventReplyEnvelope(cmd, r))) =>
          val (newEntityCount, newState) = maybeGC(state, entityCount, id, cfg.evictionThreshold, cfg.EvictionCoefficient)
          val target = newState.getOrElseUpdate(id, createChild(ctx, init, id, create))
          target ! ProcessCommand(cmd, Some(r))
          b(newState, newEntityCount, init, name, create)
        case (ctx, AggregateQuery(id, q)) =>
          val (newEntityCount, newState) = maybeGC(state, entityCount, id, cfg.evictionThreshold, cfg.EvictionCoefficient)
          val target = newState.getOrElseUpdate(id, createChild(ctx, init, id, create))
          target ! ProcessQuery(q, q.replyTo)
          b(newState,  newEntityCount,  init, name, create)
        case (ctx, GenerateAggregateInterfaces(r, _)) =>
          val ch = ctx.messageAdapter[ShardingEnvelope[_]] { m =>
            if (e3.runtimeClass.isAssignableFrom(m.message.getClass))
              AggregateQuery(m.entityId, m.message.asInstanceOf[Q])
            else if (e2.runtimeClass == classOf[EventReplyEnvelope[C@unchecked]])
              AggregateStreamCommand(m.entityId, m.message.asInstanceOf[EventReplyEnvelope[C]])
            else
              AggregateCommand(m.entityId, m.message.asInstanceOf[C])
          }
          r ! (ch, ch, ch)
          Behavior.same
      }.receiveSignal {
        case (_, Terminated(ref)) =>
          b(state.filter(e => e._2 != ref), entityCount - 1, init, name, create)
      }
    }

    b(new mutable.AnyRefMap(), 0L, init, name, create)
  }


  def clusterAggregate[A <: Entity[A, C, Q, E, R], C, Q <: Query[R], E <: Event, R](init: A, name: String, create: (A, String) => Behavior[EntityWrapperCommand[C, Q, R]])(implicit e1: ClassTag[A], e2: ClassTag[C], e3: ClassTag[Q], e4: ClassTag[E], e5: ClassTag[R], cfg: EntityConfig[C]): Behavior[AggregateMessage[C, Q, R]] = {
    Behaviors.setup { cx =>

      val sharding = ClusterSharding(cx.system)

      val typeKey = EntityTypeKey[EntityWrapperCommand[C, Q, R]](name)

      val shardRegion: ActorRef[ShardingEnvelope[EntityWrapperCommand[C, Q, R]]] = sharding.init(akka.cluster.sharding.typed.scaladsl.Entity(typeKey, createBehavior = ctx => create(init, ctx.entityId)))

      Behaviors.receive {
        case (ctx, AggregateCommand(id, cmd)) =>
          shardRegion ! ShardingEnvelope(id, ProcessCommand(cmd, None))
          Behavior.same
        case (ctx, AggregateStreamCommand(id, EventReplyEnvelope(cmd, r))) =>
          shardRegion ! ShardingEnvelope(id, ProcessCommand(cmd, Some(r)))
          Behavior.same
        case (ctx, AggregateQuery(id, q)) =>
          shardRegion ! ShardingEnvelope(id, ProcessQuery(q, q.replyTo))
          Behavior.same
        case (ctx, GenerateAggregateInterfaces(r, _)) =>
          val ch = ctx.messageAdapter[ShardingEnvelope[_]] { m =>
            if (e3.runtimeClass.isAssignableFrom(m.message.getClass))
              AggregateQuery(m.entityId, m.message.asInstanceOf[Q])
            else if (e2.runtimeClass == classOf[EventReplyEnvelope[C@unchecked]])
              AggregateStreamCommand(m.entityId, m.message.asInstanceOf[EventReplyEnvelope[C]])
            else
              AggregateCommand(m.entityId, m.message.asInstanceOf[C])
          }
          r ! (ch, ch, ch)
          Behavior.same
      }
    }
  }
}
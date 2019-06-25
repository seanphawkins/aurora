package aurora

import akka.actor.Scheduler
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{TypedActorContext, ActorRef, Behavior}
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.util.Timeout
import aurora.EntityWrapper.GenerateInterfaces

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.reflect.ClassTag

object EntitySupport {

  implicit class EntityTypedActorContext(underlying: TypedActorContext[_]) {

    implicit val timeout: Timeout = Timeout(100.seconds)
    implicit val scheduler: Scheduler = underlying.asScala.system.scheduler

    def spawnTransient[A <: Entity[A, C, Q, E, R], C, Q <: Query[R], E <: Event, R](init: A, name: String)(implicit e1: ClassTag[A], e2: ClassTag[C], e3: ClassTag[Q], e4: ClassTag[E], e5: ClassTag[R], cfg: EntityConfig[C],  timeout: Timeout, scheduler: Scheduler): (ActorRef[C], ActorRef[EventReplyEnvelope[C]], ActorRef[Q]) = {
      val a = underlying.asScala.spawn(EntityWrapper.transientEntityWrapper[A, C, Q, E, R](init), name)
      Await.result(a ? {r => GenerateInterfaces(r)}, 100.seconds)
    }

    // FIXME
    /*
    def spawnEventSourced[A <: Entity[A, C, Q, E, R], C, Q <: Query[R], E <: Event, R](init: A, name: String)(implicit e1: ClassTag[A], e2: ClassTag[C], e3: ClassTag[Q], e4: ClassTag[E], e5: ClassTag[R], cfg: EntityConfig[C], timeout: Timeout, scheduler: Scheduler): (ActorRef[C], ActorRef[EventReplyEnvelope[C]], ActorRef[Q]) = {
      val a = underlying.asScala.spawn(EntityWrapper.eventSourcedEntityWrapper[A, C, Q, E, R](init, name), name)
      Await.result(a ? {r => GenerateInterfaces(r)}, 100.seconds)
    }
    */

    def spawnPersistent[A <: Entity[A, C, Q, E, R], C, Q <: Query[R], E <: Event, R](init: A, name: String)(implicit e1: ClassTag[A], e2: ClassTag[C], e3: ClassTag[Q], e4: ClassTag[E], e5: ClassTag[R], cfg: EntityConfig[C],  timeout: Timeout, scheduler: Scheduler, pa: PersistenceAdapter[A]): (ActorRef[C], ActorRef[EventReplyEnvelope[C]], ActorRef[Q]) = {
      val a = underlying.asScala.spawn(EntityWrapper.persistentEntityWrapper[A, C, Q, E, R](init, name), name)
      Await.result(a ? {r => GenerateInterfaces(r)}, 100.seconds)
    }

    def spawnLocalAggregate[A <: Entity[A, C, Q, E, R], C, Q <: Query[R], E <: Event, R](init: A, name: String, create: (A, String) => Behavior[EntityWrapperCommand[C, Q, R]])(implicit e1: ClassTag[A], e2: ClassTag[C], e3: ClassTag[Q], e4: ClassTag[E], e5: ClassTag[R], cfg: EntityConfig[C], timeout: Timeout, scheduler: Scheduler): (ActorRef[ShardingEnvelope[C]], ActorRef[ShardingEnvelope[EventReplyEnvelope[C]]], ActorRef[ShardingEnvelope[Q]]) = {
      val a = underlying.asScala.spawn(Aggregate.localAggregate[A, C, Q, E, R](init, name, create), name)
      Await.result(a ? { r : ActorRef[(ActorRef[ShardingEnvelope[C]], ActorRef[ShardingEnvelope[EventReplyEnvelope[C]]], ActorRef[ShardingEnvelope[Q]])] => GenerateAggregateInterfaces[C, Q, R](r)}, 100.seconds)
    }

    // FIXME - Reinstate when fixed in EntityWrapper
    /*
    def spawnClusterAggregate[A <: Entity[A, C, Q, E, R], C, Q <: Query[R], E <: Event, R](init: A, name: String, create: (A, String) => Behavior[EntityWrapperCommand[C, Q, R]])(implicit e1: ClassTag[A], e2: ClassTag[C], e3: ClassTag[Q], e4: ClassTag[E], e5: ClassTag[R], cfg: EntityConfig[C], timeout: Timeout, scheduler: Scheduler): (ActorRef[ShardingEnvelope[C]], ActorRef[ShardingEnvelope[EventReplyEnvelope[C]]], ActorRef[ShardingEnvelope[Q]]) = {
      val a = underlying.asScala.spawn(Aggregate.clusterAggregate[A, C, Q, E, R](init, name, create), name)
      Await.result(a ? { r : ActorRef[(ActorRef[ShardingEnvelope[C]], ActorRef[ShardingEnvelope[EventReplyEnvelope[C]]], ActorRef[ShardingEnvelope[Q]])] => GenerateAggregateInterfaces[C, Q, R](r)}, 100.seconds)
    }

    */

  }
}
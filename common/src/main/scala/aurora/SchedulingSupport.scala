package aurora

import java.util.{Date, UUID}

import akka.actor.Props
import akka.actor.typed.ActorRef
/*
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension

trait SchedulingSupport { this: AkkaSupport =>
  val cron = QuartzSchedulerExtension(untypedSystem)

  object TypedScheduling {
    implicit class TypedScheduler(underlying: QuartzSchedulerExtension) {
      def scheduleTyped[C](name: String, receiver: ActorRef[C], msg: C, startDate: Option[Date]): java.util.Date = {
        val fakeTarget = untypedSystem.actorOf(Props(new SchedulerAdapter(receiver, msg)), UUID.randomUUID().toString)
        underlying.schedule(name, fakeTarget, "tick", startDate)
      }
    }
    private class SchedulerAdapter[C](target: ActorRef[C], msg: C) extends akka.actor.Actor {
      override def receive: Receive = {
        case _ => target ! msg
      }
    }
  }
}
*/
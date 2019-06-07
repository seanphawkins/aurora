package aurora

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}

import scala.collection.mutable
import scala.concurrent.duration._

sealed trait MetricsCollectorCommand
object MetricsCollector {
  final case class Increment(c: String) extends MetricsCollectorCommand
  final case object Flush extends MetricsCollectorCommand

  def iteratingBehavior(action: (ActorContext[MetricsCollectorCommand], (String, Long)) => Unit, d: FiniteDuration = 5.seconds): Behavior[MetricsCollectorCommand] =
    Behaviors.setup { ctx =>
      ctx.scheduleOnce(d, ctx.self, Flush)

      val cMap = new mutable.AnyRefMap[String, Long]()

      Behaviors.receive[MetricsCollectorCommand] {
        case (_, Increment(c)) =>
          val i = cMap.getOrElse(c, 0L)
          cMap.put(c, i + 1)
          Behavior.same
        case (x, Flush) =>
          cMap foreach { e =>
            action(ctx, e)
            cMap.put(e._1, 0L)
          }
          ctx.scheduleOnce(d, x.self, Flush)
          Behavior.same
      }
    }

  def logBehavior(d: FiniteDuration = 5.seconds): Behavior[MetricsCollectorCommand] =
    iteratingBehavior({ (cx: ActorContext[MetricsCollectorCommand], e) => cx.log.info("{}:{}", e._1, e._2) }, d)

  def nullBehavior: Behavior[MetricsCollectorCommand] = Behaviors.ignore
}
package aurora

import akka.actor.Scheduler
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Logger}
import akka.cluster.typed.{Cluster, ClusterSingleton}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import akka.{actor => untyped}

import scala.concurrent.ExecutionContext

trait AkkaSupport { this: ConfigSupport =>
  implicit val timeout: Timeout = Timeout(config.getDuration("akka.timeout"))
  implicit val untypedSystem: untyped.ActorSystem = untyped.ActorSystem(config.getString("akka.actorSystemName"), config)
  implicit val system: ActorSystem[Nothing] = untypedSystem.toTyped
  implicit val scheduler: Scheduler = system.scheduler
  implicit val ec: ExecutionContext = system.executionContext
  implicit val mat: ActorMaterializer = ActorMaterializer()(untypedSystem)
  implicit val log: Logger = system.log
}

trait ClusterSupport { this: AkkaSupport =>
  val cluster1 = Cluster(system)
  val singletonManager = ClusterSingleton(system)
}

trait Microservice extends ConfigSupport with AkkaSupport { this: App with MetaConfig  =>
  def logic: ActorContext[MicroserviceCommand] => Unit
}

trait SimpleMicroservice extends Microservice { this: App with MetaConfig  =>
  untypedSystem.spawn[MicroserviceCommand](Microservice.behavior(logic), "logic")
}

trait ClusteredMicrosevice extends Microservice with ClusterSupport { this: App with MetaConfig =>
  untypedSystem.spawn[MicroserviceCommand](Microservice.behavior(logic), "logic")
}

/*
trait ClusterSingletonMicroservice extends Microservice with ClusterSupport { this: App =>
  singletonManager.spawn(
    behavior = Microservice.behavior(logic),
    "logicSingleton",
    Props.empty,
    ClusterSingletonSettings(system),
    terminationMessage = Microservice.Terminate)
}
*/

sealed trait MicroserviceCommand
object Microservice {
  case object Terminate extends MicroserviceCommand
  final case class GetApplicationStatus(replyTo: ActorRef[String]) extends MicroserviceCommand
  final case class GetIssues(replyTo: ActorRef[Seq[IssueData]]) extends MicroserviceCommand

  def behavior(l: ActorContext[MicroserviceCommand] => Unit): Behavior[MicroserviceCommand] =
    Behaviors.setup { cx =>
      l(cx)
      Behaviors.receive[MicroserviceCommand] {
        case (_, GetApplicationStatus(r)) =>
          r ! AppHealth.current.toString
          Behavior.same
        case (_, GetIssues(r)) =>
          r ! AppHealth.openIssues
          Behavior.same
        case (_, Terminate) =>
          Behavior.stopped
      }
    }
}

package aurora

import akka.actor.Scheduler
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.util.Timeout
import aurora.EntitySupport._
import org.scalatest.{AsyncFlatSpec, BeforeAndAfterAll, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}

class TransientEntityTest extends AsyncFlatSpec with Matchers with BeforeAndAfterAll {
  var cci: ActorRef[CounterCommand] = _
  var sci: ActorRef[EventReplyEnvelope[CounterCommand]] = _
  var cqi: ActorRef[CounterQuery] = _
  implicit var system: ActorSystem[Any] = _
  implicit var scheduler: Scheduler = _
  implicit val timeout: Timeout = Timeout(10.seconds)
  implicit val ecx: EntityConfig[CounterCommand] = EntityConfig[CounterCommand]()

  override def beforeAll(): Unit = {
    val p = Promise[Boolean]()
    system = ActorSystem(Behaviors.setup[Any] { ctx =>
      val (ar1, ar2, ar3) = ctx.spawnTransient[Counter, CounterCommand, CounterQuery, CounterEvent, Long](Counter(0), "myTestCounter")
      cci = ar1
      sci = ar2
      cqi = ar3
      p.success(true)
      Behaviors.empty
    }, "testSystem")
    scheduler = system.scheduler
    Await.ready(p.future, timeout.duration)
  }

  override def afterAll(): Unit = system.terminate()

  "Transient Entities" should "handle basic commands and queires" in {
    (0 to 100) foreach { i => cci ! Increment }
    (cqi ? { r: ActorRef[Long] => QueryValue(r)}) map { _ should be (101) }
  }

  it should "return events from the streaming interface" in {
    (sci ? {r: ActorRef[Seq[Event]] => EventReplyEnvelope(Increment, r) }) map ( _ should be (Seq(Incremented, CommandComplete)))
  }

}

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.{ActorSystem, Scheduler}
import akka.cluster.ddata.typed.scaladsl.{DistributedData, Replicator, ReplicatorSettings}
import akka.cluster.ddata.{LWWMap, LWWMapKey, SelfUniqueAddress}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.util.Timeout
import akka.{actor => untyped}
import com.typesafe.config.ConfigFactory
import pdi.jwt.{Jwt, JwtAlgorithm}
import java.time.Clock
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import org.json4s._
import org.json4s.native.JsonMethods._


object Main extends App {
  implicit val config = ConfigFactory.load().resolve
  implicit val system = untyped.ActorSystem(config.getString("clustername"))
  implicit val mat = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  implicit val scheduler = system.scheduler
  implicit val timeout = Timeout(1.second)
  implicit val typedSystem = system.toTyped
  implicit val node = DistributedData(typedSystem).selfUniqueAddress
  implicit val replicator = system.spawn(Replicator.behavior(ReplicatorSettings(typedSystem)), "replicator")
  implicit val clock: Clock = Clock.systemUTC
  implicit val formats = DefaultFormats

  val cache = system.spawn(ConfigCache.clusteredBehavior(replicator), "configCache")

  Http().bindAndHandle(RestApi.route(cache, config.getString("kwtKey")), config.getString("httpHost"), config.getInt("httpPort"))
}

object RestApi {
  def route(cache: ActorRef[CacheCommand], key: String)(implicit system: ActorSystem, mat: ActorMaterializer, s: Scheduler, ec: ExecutionContext, timeout: Timeout, clock: Clock, formats: Formats): Route =
    headerValueByName("Authorization") { jwt =>
      val env = Jwt.decodeRawAll(jwt, key, Seq(JwtAlgorithm.HS256)).map{ case(_, j, _) => compact(render(parse(j) \ "environment")) }.getOrElse("DEV")
      path("keys") {
        get {
          complete (
            (cache ? (r => ConfigCache.ListKeys(env, r))).mapTo[Seq[String]].map(ss => "[" +ss.mkString(", ") + "]")
          )
        }
      } ~
      path("key" / Remaining) { key =>
        get {
          complete(
            (cache ? (r => ConfigCache.Get(env, key, r))).mapTo[Option[String]].map(resp => resp.getOrElse("""{}"""))
          )
        } ~
        put {
          decodeRequest {
            entity(as[String]) { value =>
              cache ! ConfigCache.Put(env, key, value)
              complete("""{"status":"OK"}""")
            }
          }
        } ~
        delete {
          cache ! ConfigCache.Delete(env, key)
          complete("""{"status":"OK"}""")
        }
      }
    }
}

sealed trait CacheCommand

object ConfigCache {
  final case class ListKeys(env: String, replyTo: ActorRef[Seq[String]]) extends CacheCommand
  final case class Get(env: String, key: String, replyTo: ActorRef[Option[String]]) extends CacheCommand
  final case class Put(env: String, key: String, data: String) extends CacheCommand
  final case class Delete(env: String, key: String) extends CacheCommand
  sealed trait InternalMsg extends CacheCommand
  private case class InternalUpdateResponse(rsp: Replicator.UpdateResponse[LWWMap[(String, String), String]]) extends InternalMsg
  private case class InternalChanged(chg: Replicator.Changed[LWWMap[(String, String), String]]) extends InternalMsg

  def ephemeralBehavior: Behavior[CacheCommand] = Behaviors.setup { ctx =>

    def b(c: Map[(String, String), String]): Behavior[CacheCommand] = Behaviors.receive {
      case (_, ListKeys(e, r)) =>
        r ! c.view.filterKeys(_._1 == e).keySet.toSeq.map(_._2)
        Behavior.same
      case (_, Get(e, k, r)) =>
        r ! c.get((e, k))
        Behavior.same
      case (_, Put(e, k, d)) =>
        b(c + ((e, k) -> d))
      case (_, Delete(e, k)) =>
        b(c - ((e, k)))
      case (_, _) =>
        Behavior.same
    }

    b(Map.empty)
  }

  // TODO - rewrite to separate by environment, multiple LWWMaps
  def clusteredBehavior(replicator: ActorRef[Replicator.Command])(implicit node: SelfUniqueAddress): Behavior[CacheCommand] = Behaviors.setup[CacheCommand] { ctx: ActorContext[CacheCommand] =>
    val mkey = LWWMapKey[(String, String), String]("ccKey")
    val updateResponseAdapter: ActorRef[Replicator.UpdateResponse[LWWMap[(String, String), String]]] =
      ctx.messageAdapter(InternalUpdateResponse.apply)
    val changedAdapter: ActorRef[Replicator.Changed[LWWMap[(String, String), String]]] =
      ctx.messageAdapter(InternalChanged.apply)
    replicator ! Replicator.Subscribe(mkey, changedAdapter)

    def b(m: LWWMap[(String, String), String], mkey: LWWMapKey[(String, String), String], updateResponseAdapter: ActorRef[Replicator.UpdateResponse[LWWMap[(String, String), String]]]): Behavior[CacheCommand] = Behaviors.receive[CacheCommand] {
      case (_, ListKeys(e, r)) =>
        r ! m.entries.keys.toSeq.collect{ case (e1, k) if e1 == e => k }
        Behavior.same
      case (_, Get(e, k, r)) =>
        r ! m.get((e, k))
        Behavior.same
      case (_, Put(e, k, v)) =>
        replicator ! Replicator.Update(mkey, m, Replicator.WriteLocal, updateResponseAdapter, None)(_ :+ ((e, k) -> v))
        Behavior.same
      case (_, Delete(e, k)) =>
        replicator ! Replicator.Update(mkey, m, Replicator.WriteLocal, updateResponseAdapter, None)(x => x.remove(node, (e, k)))
        Behavior.same
      case (_, InternalUpdateResponse(_)) =>
        Behavior.same
      case (_, InternalChanged(c)) =>
        b(c.get(mkey), mkey, updateResponseAdapter)
      case (_, _) =>
        Behavior.same
    }

    b(LWWMap.empty[(String, String), String], mkey, updateResponseAdapter)
  }
}

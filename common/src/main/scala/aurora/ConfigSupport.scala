package aurora

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import scala.concurrent.Await
import scala.concurrent.duration._

trait ConfigSupport { this: App =>
  implicit val config: Config = {
    implicit val system = ActorSystem("configTempSystem", ConfigFactory.empty)
    implicit val mat = ActorMaterializer()
    implicit val ec = system.dispatcher
    implicit val timeout = Timeout(10.second)

    val configKey = util.Properties.envOrNone("CONFIG.KEY")
    val configEnv = util.Properties.envOrElse("CONFIG.ENV", "dev")
    val configServer = util.Properties.envOrElse("CONFIG.SERVER", "localhost:9933")
    val rcs: String = configKey.map { ck =>
      Await.result(
        Http().singleRequest(HttpRequest(uri = s"http://$configServer/$configEnv/$ck")).flatMap(_.entity.toStrict(10.seconds).mapTo[String]),
        10.seconds
      )
    }.getOrElse("{}")
    val cfg = ConfigFactory.parseString(rcs)
    cfg.withFallback(ConfigFactory.load()).resolve
  }
}

package aurora

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.{Authorization, RawHeader}
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
    val token = ""
    val configKey = util.Properties.envOrNone("CONFIG.KEY")
    val configServer = util.Properties.envOrElse("CONFIG.SERVER", "localhost:9933")
    val rcs: String = configKey.map { ck =>
      Await.result(
        Http().singleRequest(HttpRequest(uri = s"http://$configServer/$ck").addHeader(RawHeader("Authorization", token))).flatMap(_.entity.toStrict(10.seconds).mapTo[String]),
        10.seconds
      )
    }.getOrElse("{}")
    ConfigFactory.parseString(rcs).withFallback(ConfigFactory.load()).resolve
  }
}

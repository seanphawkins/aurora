package aurora

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import akka.http.scaladsl.unmarshalling.Unmarshal
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.Source

trait MetaConfig {
  def configServer: Option[String]
  def configKey: String
  def token: String
}

trait EnvMetaConfig extends MetaConfig {
  val configKey = util.Properties.envOrElse("CONFIG.KEY", "DEFAULT")
  val configServer = Option(util.Properties.envOrElse("CONFIG.SERVER", "localhost:9933"))
  val tokenFile = util.Properties.envOrElse("CONFIG.KEYFILE", "./.configaccesskey")
  val token = Source.fromFile(tokenFile).getLines().mkString
}

trait LocalOnlyMetaConfig extends MetaConfig {
  def configServer: Option[String] = None
  def configKey: String = ""
  def token: String = ""
}

trait ConfigSupport { this: App with MetaConfig =>
  implicit val config: Config = {
    val rcs: String = configServer.map { csvr =>
      implicit val system = ActorSystem("configTempSystem", ConfigFactory.empty)
      implicit val mat = ActorMaterializer()
      implicit val ec = system.dispatcher
      implicit val timeout = Timeout(10.second)
      Await.result(
        Http().singleRequest(HttpRequest(uri = s"http://$csvr/$configKey").addHeader(RawHeader("Authorization", token)))
          .flatMap(resp => Unmarshal(resp.entity).to[String]),
        10.seconds)
    }.getOrElse("")
    ConfigFactory.parseString(rcs).withFallback(ConfigFactory.load()).resolve
  }
}

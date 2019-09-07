package aurora

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Matchers}

class ConfigSupportTest extends FlatSpec with Matchers {
  implicit val system = ActorSystem("ConfigSupportTest", ConfigFactory.empty)
  implicit val mat  = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  val route =
    path("TEST") {
      get {
        complete("""test.string = "foo"""")
      }
    }
  val bindingFuture = Http().bindAndHandle(route, "localhost", 9876)

  "ConfigSupport" should "return the correct config values" in {
    val testApp = new TestApp
    testApp.testConfigString shouldBe "foo"
    testApp.testConfigString2 shouldBe "bar"
  }
}

class  TestApp extends App with ConfigSupport with TestMetaConfig {
  def testConfigString = config.getString("test.string")
  def testConfigString2 = config.getString("test.string2")
}

trait TestMetaConfig extends MetaConfig {
  override def configServer = Option("localhost:9876")
  override def configKey = "TEST"
  override def token = "xxxxxxxxxxxx"
}

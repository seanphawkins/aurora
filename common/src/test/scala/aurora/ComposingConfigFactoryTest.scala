package aurora
import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Matchers}

class ComposingConfigFactoryTest extends FlatSpec with Matchers {

  "ComposingConfigFactory" should "compose config values correctly" in {
    val c1 =
      """
        |cover = 200
      """.stripMargin
    val c2 =
      """
        |a.b.c = 100
        |a.b.c = ${?cover}
      """.stripMargin
    val config = ComposingConfigFactory.parse(c1, c2)
    val config2 = ConfigFactory.parseString(c2).resolve
    config.getInt("a.b.c") shouldBe 200
    config2.getInt("a.b.c") shouldBe 100
  }

}

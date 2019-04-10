package aurora

import com.typesafe.config.{Config, ConfigFactory}

object ComposingConfigFactory {

  def parse(baseConfig: Config, ss: String*): Config =
    (baseConfig /: ss) { case (c, s) => c.withFallback(ConfigFactory.parseString(s)).resolve }

  def parse(ss: String*): Config = parse(ConfigFactory.empty, ss:_*)

}
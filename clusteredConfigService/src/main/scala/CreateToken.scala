import java.time.Clock

import pdi.jwt.{Jwt, JwtAlgorithm}

object CreateToken extends App {
  implicit val clock: Clock = Clock.systemUTC
  println(Jwt.encode(s"""{"environment":"${args(0)}"}""", args(1), JwtAlgorithm.HS256))
}

import authentikat.jwt._

object CreateToken extends App {
  val header = JwtHeader("HS256")
  val claimsSet = JwtClaimsSet(Map("environment" -> args(0)))
  println(JsonWebToken(header, claimsSet, args(1)))
}

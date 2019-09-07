package aurora

import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import com.github.plokhotnyuk.jsoniter_scala.core._
import com.github.plokhotnyuk.jsoniter_scala.macros._


trait AppHealthSupport { this: AkkaSupport with ConfigSupport =>

  val route = {
    implicit val codec: JsonValueCodec[Seq[IssueData]] = JsonCodecMaker.make[Seq[IssueData]](CodecMakerConfig())
    path("status") {
      get {
        complete(AppHealth.current.toString)
      }
    } ~
      path("issues") {
        get {
          complete(writeToArray(AppHealth.openIssues).map(_.toChar).mkString)
        }
      } ~
      path("issue"/ IntNumber) { id =>
        get {
          complete(writeToArray(AppHealth.openIssues.filter(_.id == new IssueId(id))).map(_.toChar).mkString)
        } ~
          delete {
            AppHealth.clear(new IssueId(id))
            complete("OK")
          }
      } ~
      path("issue"/ Remaining) { id =>
        get {
          complete(writeToArray(AppHealth.openIssues.filter(_.correlationId == id)).map(_.toChar).mkString)
        } ~
          delete {
            AppHealth.clear(id)
            complete("OK")
          }
      }
  }

  val appHealthServerBindingFuture = Http().bindAndHandle(route, config.getString("apphealth.host"), config.getInt("apphealth.port"))
}

package pl.pieczka.common

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import akka.util.Timeout

import concurrent.duration._
import scala.concurrent.ExecutionContext

trait GroupFeedRoutesDefinition {

  implicit val endpointTimeout: Timeout = Timeout(10.seconds)

  /**
   * Returns the routes defined for this endpoint
   *
   * @param system       The implicit system to use for building routes
   * @param ec           The implicit execution context to use for routes
   * @param materializer The implicit materializer to use for routes
   */
  def routes(implicit system: ActorSystem, ec: ExecutionContext, materializer: Materializer): Route

}

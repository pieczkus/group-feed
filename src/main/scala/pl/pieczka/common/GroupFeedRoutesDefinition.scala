package pl.pieczka.common

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.StatusCodes.OK
import akka.http.scaladsl.server.Directives.{complete, onComplete}
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import akka.util.Timeout
import pl.pieczka.common.PersistentEntity.MaybeState
import spray.json.JsonFormat

import concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

trait GroupFeedRoutesDefinition extends GroupFeedJsonProtocol {

  implicit val endpointTimeout: Timeout = Timeout(10.seconds)

  /**
   * Returns the routes defined for this endpoint
   *
   * @param system       The implicit system to use for building routes
   * @param ec           The implicit execution context to use for routes
   * @param materializer The implicit materializer to use for routes
   */
  def routes(implicit system: ActorSystem, ec: ExecutionContext, materializer: Materializer): Route


  def service[T: ClassTag](msg: Any, ref: ActorRef): Future[MaybeState[T]] = {
    import akka.pattern.ask
    (ref ? msg).mapTo[MaybeState[T]]
  }

  def serviceAndComplete[T: ClassTag](msg: Any, ref: ActorRef)(implicit format: JsonFormat[T]): Route = {
    val fut = service[T](msg, ref)
    onComplete(fut) {
      case util.Success(Right(state)) =>
        complete((StatusCodes.OK, ApiResponse(state)))

      case util.Success(Left(failure)) =>
        complete((StatusCodes.NotFound, failure.message))

      case util.Failure(ex) =>
        complete((StatusCodes.InternalServerError, "Something went wrong, please try again later"))
    }
  }

}

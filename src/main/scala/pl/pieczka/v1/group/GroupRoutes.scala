package pl.pieczka.v1.group

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import pl.pieczka.common.{GroupFeedRoutesDefinition, User}
import pl.pieczka.v1.group.GroupEntity.MaybeGroup
import pl.pieczka.v1.user.UsersManager.RegisterUser

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}


class GroupRoutes(groupsManager: ActorRef)(implicit val ec: ExecutionContext) extends GroupFeedRoutesDefinition with GroupJsonProtocol {

  import akka.http.scaladsl.server.Directives._
  import akka.pattern.ask

  /**
   * Returns the routes defined for this endpoint
   *
   * @param system       The implicit system to use for building routes
   * @param ec           The implicit execution context to use for routes
   * @param materializer The implicit materializer to use for routes
   */
  override def routes(implicit system: ActorSystem, ec: ExecutionContext, materializer: Materializer): Route = {
    pathPrefix("group") {
      get {
        path(IntNumber) { groupId =>
          onComplete((groupsManager ? GroupsManager.FindGroupById(groupId)).mapTo[MaybeGroup[GroupState]]) {
            case Success(result) => result match {
              case Right(group) => complete((StatusCodes.OK, group))
              case Left(error) => complete((StatusCodes.NotFound, error.toString))
            }
            case Failure(error) => complete((StatusCodes.ServiceUnavailable, error))
          }
        } ~ path(IntNumber / "feed") { groupId =>
          onComplete((groupsManager ? GroupsManager.FindGroupById(groupId)).mapTo[MaybeGroup[GroupState]]) {
            case Success(result) => result match {
              case Right(group) => complete((StatusCodes.OK, group.feed))
              case Left(error) => complete((StatusCodes.NotFound, error.toString))
            }
            case Failure(error) => complete((StatusCodes.ServiceUnavailable, error))
          }
        }
      } ~ post {
        path(IntNumber / "feed") {groupId =>
          entity(as[MessageInput]) { message =>
            onComplete((groupsManager ? GroupsManager.PostMessage(groupId, message.user.id, message)).mapTo[MaybeGroup[GroupState]]) {
              case Success(result) => result match {
                case Right(group) => complete((StatusCodes.OK, group))
                case Left(error) => complete((StatusCodes.NotFound, error.toString))
              }
              case Failure(error) => complete((StatusCodes.ServiceUnavailable, error))
            }
          }
        }
      }
    }
  }
}


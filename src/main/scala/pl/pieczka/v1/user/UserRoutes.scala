package pl.pieczka.v1.user

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import pl.pieczka.common.auth.AuthDirectives
import pl.pieczka.common.{GroupFeedRoutesDefinition, User}
import pl.pieczka.v1.user.UserEntity.MaybeUser

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class UserRoutes(usersManager: ActorRef)(implicit val ec: ExecutionContext) extends GroupFeedRoutesDefinition with UserJsonProtocol with AuthDirectives {

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
    pathPrefix("user") {
      get {
        path(IntNumber) { id =>
          onComplete((usersManager ? UsersManager.FindUserById(id)).mapTo[UserEntity.MaybeUser[UserState]]) {
            case Success(result) => result match {
              case Right(user) => complete((StatusCodes.OK, user))
              case Left(error) => complete((StatusCodes.NotFound, error.message))
            }
            case Failure(error) => complete((StatusCodes.ServiceUnavailable, error))
          }
        } ~ path("group") {
          authenticate { userId =>
            onComplete((usersManager ? UsersManager.FindUserById(userId)).mapTo[UserEntity.MaybeUser[UserState]]) {
              case Success(result) => result match {
                case Right(user) => complete((StatusCodes.OK, user.groups))
                case Left(error) => complete((StatusCodes.NotFound, error.message))
              }
              case Failure(error) => complete((StatusCodes.ServiceUnavailable, error))
            }
          }
        } ~ path("feed") {
          authenticate { userId =>
            onComplete((usersManager ? UsersManager.FindUserById(userId)).mapTo[UserEntity.MaybeUser[UserState]]) {
              case Success(result) => result match {
                case Right(user) => complete((StatusCodes.OK, user.feed))
                case Left(error) => complete((StatusCodes.NotFound, error.message))
              }
              case Failure(error) => complete((StatusCodes.ServiceUnavailable, error))
            }
          }
        }
      } ~
        post {
          entity(as[User]) { user =>
            onComplete((usersManager ? UsersManager.RegisterUser(user.id, user.name)).mapTo[UserEntity.MaybeUserCreated[UserState]]) {
              case Success(result) => result match {
                case Right(user) => complete((StatusCodes.OK, user))
                case Left(error) => complete((StatusCodes.NotFound, error.message))
              }
              case Failure(error) => complete((StatusCodes.ServiceUnavailable, error))
            }
          } ~ path("group") {
            authenticate { userId =>
              entity(as[JoinGroupInput]) { joinGroupInput =>
                onComplete((usersManager ? UsersManager.JoinGroup(userId, joinGroupInput.groupId)).mapTo[UserEntity.MaybeUser[UserState]]) {
                  case Success(result) => result match {
                    case Right(user) => complete((StatusCodes.OK, user.groups))
                    case Left(error) => complete((StatusCodes.NotFound, error.message))
                  }
                  case Failure(error) => complete((StatusCodes.ServiceUnavailable, error))
                }
              }
            }
          }
        } ~ delete {
        path("group" / IntNumber) { groupId =>
          authenticate { userId =>
            onComplete((usersManager ? UsersManager.LeaveGroup(userId, groupId)).mapTo[UserEntity.MaybeUser[UserState]]) {
              case Success(result) => result match {
                case Right(user) => complete((StatusCodes.OK, user))
                case Left(error) => complete((StatusCodes.NotFound, error.message))
              }
              case Failure(error) => complete((StatusCodes.ServiceUnavailable, error))
            }
          }
        }
      }
    }
  }

  override def verifyToken(token: String): Future[Option[Int]] = {
    (usersManager ? UsersManager.FindUserByToken(token)).mapTo[MaybeUser[UserState]].map {
      case Right(user) => Some(user.id)
      case _ => None
    }
  }
}

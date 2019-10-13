package pl.pieczka.v1.group

import akka.actor.{ActorRef, ActorSystem}
import akka.cluster.sharding.ClusterSharding
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import pl.pieczka.common.auth.AuthDirectives
import pl.pieczka.common.GroupFeedRoutesDefinition
import pl.pieczka.v1.group.GroupEntity.MaybeGroup
import pl.pieczka.v1.user.UserEntity.MaybeUser
import pl.pieczka.v1.user.{UserEntity, UserState}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


class GroupRoutes(groupsManager: ActorRef)(implicit val ec: ExecutionContext, system: ActorSystem) extends GroupFeedRoutesDefinition with GroupJsonProtocol with AuthDirectives {

  import akka.http.scaladsl.server.Directives._
  import akka.pattern.ask

  lazy val userShardRegion = ClusterSharding(system).shardRegion("UserEntity")

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
        authenticate { userId =>
          path(IntNumber) { groupId =>
            onComplete((groupsManager ? GroupsManager.FindGroupById(groupId, userId)).mapTo[MaybeGroup[GroupState]]) {
              case Success(result) => result match {
                case Right(group) => complete((StatusCodes.OK, group))
                case Left(error) => complete((StatusCodes.NotFound, error.message))
              }
              case Failure(error) => complete((StatusCodes.ServiceUnavailable, error))
            }
          } ~ path(IntNumber / "feed") { groupId =>
            onComplete((groupsManager ? GroupsManager.FindGroupById(groupId, userId)).mapTo[MaybeGroup[GroupState]]) {
              case Success(result) => result match {
                case Right(group) => complete((StatusCodes.OK, group.feed))
                case Left(error) => complete((StatusCodes.NotFound, error.message))
              }
              case Failure(error) => complete((StatusCodes.ServiceUnavailable, error))
            }
          }
        }
      } ~ post {
        path(IntNumber / "feed") {groupId =>
          entity(as[MessageInput]) { message =>
            onComplete((groupsManager ? GroupsManager.PostMessage(groupId, message.user.id, message)).mapTo[MaybeGroup[GroupState]]) {
              case Success(result) => result match {
                case Right(group) => complete((StatusCodes.OK, group))
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
    (userShardRegion ? UserEntity.GetUser(token.toIntOption.getOrElse(0))).mapTo[MaybeUser[UserState]].map {
      case Right(user) => Some(user.id)
      case _ => None
    }
  }
}


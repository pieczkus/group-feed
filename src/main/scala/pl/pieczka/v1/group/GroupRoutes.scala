package pl.pieczka.v1.group

import akka.actor.{ActorRef, ActorSystem}
import akka.cluster.sharding.ClusterSharding
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import pl.pieczka.common.auth.AuthDirectives
import pl.pieczka.common.{GroupFeedRoutesDefinition, Message}
import pl.pieczka.common.PersistentEntity.MaybeState
import pl.pieczka.v1.user.{UserEntity, UserState}

import scala.concurrent.{ExecutionContext, Future}

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
            serviceAndComplete[GroupState](GroupsManager.FindGroupById(groupId, userId), groupsManager)
          } ~ path(IntNumber / "feed") { groupId =>
            serviceAndComplete[Seq[Message]](GroupsManager.GetFeed(groupId, userId), groupsManager)
          }
        }
      } ~ post {
        path(IntNumber / "feed") { groupId =>
          authenticate { userId =>
            entity(as[MessageInput]) { message =>
              serviceAndComplete[GroupState](GroupsManager.PostMessage(groupId, userId, message), groupsManager)
            }
          }
        }
      }
    }
  }

  override def verifyToken(token: String): Future[Option[Int]] = {
    (userShardRegion ? UserEntity.GetUser(token.toIntOption.getOrElse(0))).mapTo[MaybeState[UserState]].map {
      case Right(user) => Some(user.id)
      case _ => None
    }
  }
}


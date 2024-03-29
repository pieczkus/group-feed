package pl.pieczka.v1.user

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import pl.pieczka.common.PersistentEntity.MaybeState
import pl.pieczka.common.auth.AuthDirectives
import pl.pieczka.common.{GroupFeedRoutesDefinition, Message, PagingDirectives, User}

import scala.concurrent.{ExecutionContext, Future}

class UserRoutes(usersManager: ActorRef)(implicit val ec: ExecutionContext) extends GroupFeedRoutesDefinition
  with UserJsonProtocol with AuthDirectives with PagingDirectives {

  import akka.http.scaladsl.server.Directives._
  import akka.pattern.ask

  override def routes(implicit system: ActorSystem, ec: ExecutionContext, materializer: Materializer): Route = {
    pathPrefix("user") {
      get {
        path(IntNumber) { userId =>
          serviceAndComplete[UserState](UsersManager.FindUserById(userId), usersManager)
        } ~ path("group") {
          authenticate { userId =>
            serviceAndComplete[Set[Int]](UsersManager.FindUserGroups(userId), usersManager)
          }
        } ~ path("feed") {
          pageParams { pageParams =>
            authenticate { userId =>
              serviceAndComplete[Seq[Message]](UsersManager.FindUserFeed(userId, pageParams), usersManager)
            }
          }
        }
      } ~
        post {
          entity(as[User]) { user =>
            serviceAndComplete[UserState](UsersManager.RegisterUser(user.id, user.name), usersManager)
          } ~ path("group") {
            authenticate { userId =>
              entity(as[JoinGroupInput]) { joinGroupInput =>
                serviceAndComplete[UserState](UsersManager.JoinGroup(userId, joinGroupInput.groupId), usersManager)
              }
            }
          }
        } ~ delete {
        path("group" / IntNumber) { groupId =>
          authenticate { userId =>
            serviceAndComplete[UserState](UsersManager.LeaveGroup(userId, groupId), usersManager)
          }
        }
      }
    }
  }

  override def verifyToken(token: String): Future[Option[Int]] = {
    (usersManager ? UsersManager.FindUserByToken(token)).mapTo[MaybeState[UserState]].map {
      case Right(user) => Some(user.id)
      case _ => None
    }
  }
}

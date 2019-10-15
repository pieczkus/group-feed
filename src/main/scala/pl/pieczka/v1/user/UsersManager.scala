package pl.pieczka.v1.user

import akka.actor.Props
import akka.util.Timeout
import pl.pieczka.common.Aggregate

import scala.concurrent.duration._
import akka.pattern.ask
import pl.pieczka.common.PersistentEntity.MaybeState
import Math.abs

object UsersManager {

  val name = "UsersManager"

  def props = Props[UsersManager]

  case class RegisterUser(userId: Int, name: String)

  case class FindUserById(userId: Int)

  case class JoinGroup(userId: Int, groupId: Int)

  case class LeaveGroup(userId: Int, groupId: Int)

  case class FindUserByToken(token: String)

  case class FindUserGroups(userId: Int)

  case class FindUserFeed(userId: Int)

}

class UsersManager extends Aggregate[UserState, UserEntity] {

  import UsersManager._
  import context.dispatcher

  implicit val endpointTimeout: Timeout = Timeout(10.seconds)

  override def entityProps: Props = UserEntity.props

  override def receive: Receive = {
    case RegisterUser(userId, name) =>
      entityShardRegion.forward(UserEntity.CreateUser(abs(userId), name))

    case FindUserById(userId) =>
      entityShardRegion.forward(UserEntity.GetUser(abs(userId)))

    case FindUserByToken(token) =>
      entityShardRegion.forward(UserEntity.GetUser(token.toIntOption.getOrElse(0)))

    case JoinGroup(userId, groupId) =>
      entityShardRegion.forward(UserEntity.AddGroup(abs(userId), abs(groupId)))

    case LeaveGroup(userId, groupId) =>
      entityShardRegion.forward(UserEntity.RemoveGroup(abs(userId), abs(groupId)))

    case FindUserGroups(userId) =>
      val caller = sender()
      (entityShardRegion ? UserEntity.GetUser(abs(userId))).mapTo[MaybeState[UserState]].map {
        case Right(user) => caller ! Right(user.groups)
        case l@Left(_) => caller ! l
      }

    case FindUserFeed(userId) =>
      val caller = sender()
      (entityShardRegion ? UserEntity.GetUser(abs(userId))).mapTo[MaybeState[UserState]].map {
        case Right(user) => caller ! Right(user.feed)
        case l@Left(_) => caller ! l
      }
  }

}

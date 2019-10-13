package pl.pieczka.v1.user

import akka.actor.Props
import akka.util.Timeout
import pl.pieczka.common.Aggregate
import scala.concurrent.duration._

object UsersManager {

  val name = "UsersManager"

  def props = Props[UsersManager]

  case class RegisterUser(userId: Int, name: String)

  case class FindUserById(userId: Int)

  case class JoinGroup(userId: Int, groupId: Int)

  case class LeaveGroup(userId: Int, groupId: Int)

  case class FindUserByToken(token: String)

}

class UsersManager extends Aggregate[UserEntity] {

  import UsersManager._

  implicit val endpointTimeout: Timeout = Timeout(10.seconds)

  override def entityProps: Props = UserEntity.props

  override def receive: Receive = {
    case RegisterUser(userId, name) =>
      entityShardRegion.forward(UserEntity.CreateUser(userId, name))

    case FindUserById(userId) =>
      entityShardRegion.forward(UserEntity.GetUser(userId))

    case FindUserByToken(token) =>
      entityShardRegion.forward(UserEntity.GetUser(token.toIntOption.getOrElse(0)))

    case JoinGroup(userId, groupId) =>
      entityShardRegion.forward(UserEntity.AddGroup(userId, groupId))

    case LeaveGroup(userId, groupId) =>
      entityShardRegion.forward(UserEntity.RemoveGroup(userId, groupId))

  }

}

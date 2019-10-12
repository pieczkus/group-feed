package pl.pieczka.v1.group

import akka.actor.Props
import akka.util.Timeout
import pl.pieczka.common.Aggregate

import scala.concurrent.duration._

object GroupsManager {

  val name = "GroupsManager"

  def props = Props[GroupsManager]

  case class FindGroupById(groupId: Int)

  case class JoinGroup(groupId: Int, userId: Int)

  case class LeaveGroup(groupId: Int, userId: Int)

  case class PostMessage(groupId: Int, userId: Int, content: String)

}

class GroupsManager extends Aggregate[GroupEntity] {

  import GroupsManager._

  implicit val endpointTimeout: Timeout = Timeout(10.seconds)

  override def entityProps: Props = GroupEntity.props

  override def receive: Receive = {
    case FindGroupById(groupId) =>
      entityShardRegion.forward(GroupEntity.GetGroup(groupId))

    case JoinGroup(groupId, userId) =>
      entityShardRegion.forward(GroupEntity.AddUser(groupId, userId))

    case LeaveGroup(groupId, userId) =>
      entityShardRegion.forward(GroupEntity.RemoveUser(groupId, userId))

    case PostMessage(groupId, userId, content) =>
      entityShardRegion.forward(GroupEntity.AddMessage(groupId, userId, content))

  }
}

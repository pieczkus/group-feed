package pl.pieczka.v1.group

import java.util.UUID

import akka.actor.Props
import akka.util.Timeout
import pl.pieczka.common.{Aggregate, Message}

import scala.concurrent.duration._

object GroupsManager {

  val name = "GroupsManager"

  def props = Props[GroupsManager]

  case class FindGroupById(groupId: Int, userId: Int)

  case class PostMessage(groupId: Int, userId: Int, messageInput: MessageInput)

  case class GetFeed(groupId: Int, userId: Int)

}

class GroupsManager extends Aggregate[GroupEntity] {

  import GroupsManager._

  implicit val endpointTimeout: Timeout = Timeout(10.seconds)

  override def entityProps: Props = GroupEntity.props

  override def receive: Receive = {
    case FindGroupById(groupId, userId) =>
      entityShardRegion.forward(GroupEntity.GetGroup(groupId, userId))

    case PostMessage(groupId, userId, messageInput) =>
      val id = UUID.randomUUID().toString
      val message = Message(id, groupId, messageInput.content, messageInput.user)
      entityShardRegion.forward(GroupEntity.AddMessage(groupId, userId, message))

    case GetFeed(groupId, userId) =>
      entityShardRegion.forward((GroupEntity.GetMessages(groupId, userId)))

  }
}

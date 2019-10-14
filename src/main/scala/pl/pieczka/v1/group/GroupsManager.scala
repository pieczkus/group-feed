package pl.pieczka.v1.group

import java.util.UUID

import akka.actor.Props
import akka.util.Timeout
import pl.pieczka.common.PersistentEntity.MaybeState
import pl.pieczka.common.{Aggregate, Message}
import akka.pattern.ask
import pl.pieczka.v1.group.GroupEntity.NotMember

import scala.concurrent.duration._

object GroupsManager {

  val name = "GroupsManager"

  def props = Props[GroupsManager]

  case class FindGroupById(groupId: Int, userId: Int)

  case class PostMessage(groupId: Int, userId: Int, messageInput: MessageInput)

  case class GetFeed(groupId: Int, userId: Int)

}

class GroupsManager extends Aggregate[GroupState, GroupEntity] {

  import GroupsManager._
  import context.dispatcher

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
      val caller = sender()
      (entityShardRegion ? GroupEntity.GetGroup(groupId, userId)).mapTo[MaybeState[GroupState]].map {
        case Right(group) if !group.members.contains(userId) => caller ! Left(NotMember(groupId, userId))
        case Right(group) => caller ! Right(group.feed)
        case l@Left(_) => caller ! l
      }

  }
}

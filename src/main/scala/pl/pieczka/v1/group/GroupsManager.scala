package pl.pieczka.v1.group

import java.util.UUID

import akka.actor.Props
import akka.util.Timeout
import pl.pieczka.common.PersistentEntity.MaybeState
import pl.pieczka.common.{Aggregate, Message, PageParams}
import akka.pattern.ask
import pl.pieczka.v1.group.GroupEntity.NotMember
import Math.abs

import scala.concurrent.duration._

object GroupsManager {

  val name = "GroupsManager"

  def props = Props[GroupsManager]

  case class CreateNewGroup(groupId: Int, userId: Int)

  case class FindGroupById(groupId: Int, userId: Int)

  case class PostMessage(groupId: Int, userId: Int, messageInput: MessageInput)

  case class GetFeed(groupId: Int, userId: Int, pageParams: PageParams)

}

class GroupsManager extends Aggregate[GroupState, GroupEntity] {

  import GroupsManager._
  import context.dispatcher

  implicit val endpointTimeout: Timeout = Timeout(10.seconds)

  override def entityProps: Props = GroupEntity.props

  override def receive: Receive = {

    case CreateNewGroup(groupId, userId) =>
      entityShardRegion.forward(GroupEntity.CreateGroup(abs(groupId), abs(userId)))

    case FindGroupById(groupId, userId) =>
      entityShardRegion.forward(GroupEntity.GetGroup(abs(groupId), abs(userId)))

    case PostMessage(groupId, userId, messageInput) =>
      val id = UUID.randomUUID().toString
      val message = Message(id, groupId, messageInput.content, messageInput.user)
      entityShardRegion.forward(GroupEntity.AddMessage(abs(groupId), abs(userId), message))

    case GetFeed(groupId, userId, pageParams) =>
      val caller = sender()
      (entityShardRegion ? GroupEntity.GetGroup(abs(groupId), abs(userId))).mapTo[MaybeState[GroupState]].map {
        case Right(group) if !group.members.contains(userId) => caller ! Left(NotMember(groupId, userId))
        case Right(group) => caller ! Right(group.feed.slice(pageParams.skip, pageParams.skip + pageParams.limit))
        case l@Left(_) => caller ! l
      }

  }
}

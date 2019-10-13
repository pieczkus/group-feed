package pl.pieczka.v1.group

import akka.actor.Props
import akka.cluster.pubsub.DistributedPubSub
import pl.pieczka.common.{Message, PersistentEntity, UserGroupAssociation}

case class GroupState(members: Set[Int] = Set.empty, feed: Seq[Message] = Seq.empty)

object GroupEntity {

  def props: Props = Props[GroupEntity]

  val entityType = "group"

  sealed trait GroupCommand extends PersistentEntity.EntityCommand {
    val groupId: Int

    override def entityId: String = groupId.toString
  }

  case class GetGroup(groupId: Int) extends GroupCommand


  case class AddUser(groupId: Int, userId: Int) extends GroupCommand

  case class RemoveUser(groupId: Int, userId: Int) extends GroupCommand

  case class AddMessage(groupId: Int, userId: Int, message: Message) extends GroupCommand

  case class GetMessages(groupId: Int, userId: Int) extends GroupCommand

  sealed trait GroupEvent extends PersistentEntity.EntityEvent

  case class UserAdded(groupId: Int, userId: Int) extends GroupEvent

  case class UserRemoved(groupId: Int, userId: Int) extends GroupEvent

  case class MessageAdded(groupId: Int, userId: Int, message: Message) extends GroupEvent

  sealed trait GroupFailure {
    val groupId: Int

    def message: String
  }

  case class GroupNotFound(groupId: Int) extends GroupFailure {
    override def message = s"Group with id $groupId not found"
  }

  case class GroupAlreadyExists(groupId: Int) extends GroupFailure {
    override def message = s"Group with id $groupId already exists"
  }

  case class NotMember(groupId: Int, userId: Int) extends GroupFailure {
    override def message = s"User with id $userId is not member of group $groupId"
  }

  type MaybeGroup[+A] = Either[GroupFailure, A]

}

class GroupEntity extends PersistentEntity {

  import GroupEntity._
  import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe}

  val mediator = DistributedPubSub(context.system).mediator
  mediator ! Subscribe("user-groups", self)

  private var state = GroupState()

  override def additionalCommandHandling: Receive = {

    case GetGroup(_) =>
      log.info("name {}", self.path.name)
      sender() ! Right(state)

    case AddMessage(groupId, userId, _) if !state.members.contains(userId) => sender() ! Left(NotMember(groupId, userId))

    case AddMessage(groupId, userId, message) =>
      val caller = sender()
      persist(MessageAdded(groupId, userId, message)) { evt =>
        log.debug("Message \"{}\" added to {} by user {}", evt.message, evt.groupId, evt.userId)
        handleEvent(evt)
        mediator ! Publish(s"group_${evt.groupId}", evt.message)
        caller ! Right(state)
      }

    case GetMessages(groupId, userId) if !state.members.contains(userId) => sender() ! Left(NotMember(groupId, userId))

    case GetMessages(_, _) =>
      sender() ! state.feed

    case UserGroupAssociation(userId, groupId)  =>
      log.info("dupodongo")
      persist(UserAdded(groupId, userId)) { evt =>
        log.info("User {} joined group {}", evt.userId, evt.groupId)
        handleEvent(evt)
      }
  }

  override def handleEvent(event: PersistentEntity.EntityEvent): Unit = event match {
    case UserAdded(_, userId) => state = state.copy(members = state.members + userId)
    case UserRemoved(_, userId) => state = state.copy(members = state.members - userId)
    case MessageAdded(_, _, message) => state = state.copy(feed = message +: state.feed)
  }
}

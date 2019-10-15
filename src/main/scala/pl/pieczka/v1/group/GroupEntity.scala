package pl.pieczka.v1.group

import akka.actor.Props
import akka.cluster.pubsub.DistributedPubSub
import pl.pieczka.common.PersistentEntity.Failure
import pl.pieczka.common.{EntityStateObject, Message, PersistentEntity, UserGroupAssociation}

object GroupState {
  def empty: GroupState = GroupState(-1)
}

case class GroupState(id: Int, members: Set[Int] = Set.empty, feed: Seq[Message] = Seq.empty) extends EntityStateObject[Int] {
  def isEmpty: Boolean = id < 0
}

object GroupEntity {

  def props: Props = Props[GroupEntity]

  val entityType = "group"

  sealed trait GroupCommand extends PersistentEntity.EntityCommand {
    val groupId: Int

    override def entityId: String = groupId.toString
  }

  case class GetGroup(groupId: Int, userId: Int) extends GroupCommand

  case class CreateGroup(groupId: Int, userId: Int) extends GroupCommand

  case class AddUser(groupId: Int, userId: Int) extends GroupCommand

  case class RemoveUser(groupId: Int, userId: Int) extends GroupCommand

  case class AddMessage(groupId: Int, userId: Int, message: Message) extends GroupCommand

  sealed trait GroupEvent extends PersistentEntity.EntityEvent

  case class GroupCreated(groupId: Int, userId: Int) extends GroupEvent

  case class UserAdded(groupId: Int, userId: Int) extends GroupEvent

  case class UserRemoved(groupId: Int, userId: Int) extends GroupEvent

  case class MessageAdded(groupId: Int, userId: Int, message: Message) extends GroupEvent


  case class GroupNotFound(groupId: Int) extends Failure {
    override val id: Int = groupId

    override def message = s"Group with id $groupId not found"
  }

  case class GroupAlreadyExists(groupId: Int) extends Failure {
    override val id: Int = groupId

    override def message = s"Group with id $groupId already exists"
  }

  case class NotMember(groupId: Int, userId: Int) extends Failure {
    override val id: Int = groupId

    override def message = s"User with id $userId is not member of group $groupId"
  }

}

class GroupEntity extends PersistentEntity[GroupState] {

  import GroupEntity._
  import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe}

  private val mediator = DistributedPubSub(context.system).mediator
  mediator ! Subscribe("user-groups", self)

  var state = GroupState.empty

  override def additionalCommandHandling: Receive = {

    case CreateGroup(groupId, _) if !state.isEmpty =>
      sender() ! Left(GroupAlreadyExists(groupId))

    case CreateGroup(groupId, userId) =>
      persist(GroupCreated(groupId, userId)) { evt =>
        log.info("Group {} created by user {}", evt.groupId, evt.userId)
        handleEvent(evt)
        sender() ! Right(state)
      }

    case GetGroup(groupId, _) if state.isEmpty =>
      sender() ! Left(GroupNotFound(groupId))

    case GetGroup(groupId, userId) if !state.members.contains(userId) =>
      sender() ! Left(NotMember(groupId, userId))

    case GetGroup(_, _) =>
      sender() ! Right(state)

    case AddMessage(groupId, userId, _) if !state.members.contains(userId) =>
      sender() ! Left(NotMember(groupId, userId))

    case AddMessage(groupId, userId, message) =>
      persist(MessageAdded(groupId, userId, message)) { evt =>
        log.debug("Message \"{}\" added to {} by user {}", evt.message, evt.groupId, evt.userId)
        handleEventAndMaybeSnapshot(evt)
        mediator ! Publish(s"group_${evt.groupId}", evt.message)
        sender() ! Right(state)
      }

    case UserGroupAssociation(userId, groupId) if groupId == state.id =>
      persist(UserAdded(groupId, userId)) { evt =>
        log.info("User {} joined group {}", evt.userId, evt.groupId)
        handleEventAndMaybeSnapshot(evt)
      }
  }

  override def handleEvent(event: PersistentEntity.EntityEvent): Unit = event match {
    case GroupCreated(groupId, userId) => state = state.copy(id = groupId, members = state.members + userId)
    case UserAdded(_, userId) => state = state.copy(members = state.members + userId)
    case UserRemoved(_, userId) => state = state.copy(members = state.members - userId)
    case MessageAdded(_, _, message) => state = state.copy(feed = message +: state.feed)
  }

  override def snapshotAfterCount: Option[Int] = Some(200)

}

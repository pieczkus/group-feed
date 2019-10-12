package pl.pieczka.v1.group

import akka.actor.Props
import pl.pieczka.common.PersistentEntity

object GroupState {
  def empty: GroupState = GroupState(-1)
}

case class GroupState(id: Int, members: Set[Int] = Set.empty, feed: Seq[String] = Seq.empty) {

  def isEmpty: Boolean = id < 0
}

object GroupEntity {

  def props: Props = Props[GroupEntity]

  val entityType = "group"

  sealed trait GroupCommand extends PersistentEntity.EntityCommand {
    val groupId: Int

    override def entityId: String = groupId.toString
  }

  case class GetGroup(groupId: Int) extends GroupCommand

  case class CreateGroup(groupId: Int) extends GroupCommand

  case class AddUser(groupId: Int, userId: Int) extends GroupCommand

  case class RemoveUser(groupId: Int, userId: Int) extends GroupCommand

  case class AddMessage(groupId: Int, userId: Int, content: String) extends GroupCommand

  case class GetFeed(groupId: Int, userId: Int) extends GroupCommand

  sealed trait GroupEvent extends PersistentEntity.EntityEvent

  case class GroupCreated(group: GroupState) extends GroupEvent

  case class UserAdded(groupId: Int, userId: Int) extends GroupEvent

  case class UserRemoved(groupId: Int, userId: Int) extends GroupEvent

  case class MessageAdded(groupId: Int, userId: Int, content: String) extends GroupEvent

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

  private var state = GroupState.empty

  override def additionalCommandHandling: Receive = {
    case GetGroup(groupId) if state.isEmpty => sender() ! Left(GroupNotFound(groupId))

    case GetGroup(_) => sender() ! Right(state)

    case CreateGroup(groupId) if state.isEmpty =>
      val caller = sender()
      persist(GroupCreated(GroupState(groupId))) { evt =>
        log.info("New group created {}", evt.group.id)
        handleEvent(evt)
        caller ! Right(state)
      }

    case AddUser(groupId, userId) =>
      val caller = sender()
      persist(UserAdded(groupId, userId)) { evt =>
        log.info("User {} joined group {}", evt.userId, evt.groupId)
        handleEvent(evt)
        caller ! Right(state)
      }

    case RemoveUser(groupId, userId) =>
      val caller = sender()
      persist(UserRemoved(groupId, userId)) { evt =>
        log.info("User {} left group {}", evt.userId, evt.groupId)
        handleEvent(evt)
        caller ! Right(state)
      }

    case AddMessage(groupId, userId, _) if !state.members.contains(userId) => sender() ! Left(NotMember(groupId, userId))

    case AddMessage(groupId, userId, content) =>
      val caller = sender()
      persist(MessageAdded(groupId, userId, content)) { evt =>
        log.debug("Message \"{}\" added to {} by user {}", evt.content, evt.groupId, evt.userId)
        handleEvent(evt)
        caller ! Right(state)
      }

    case GetFeed(groupId, userId) if !state.members.contains(userId) => sender() ! Left(NotMember(groupId, userId))

    case GetFeed(_, _) =>
      sender() ! state.feed

  }

  override def handleEvent(event: PersistentEntity.EntityEvent): Unit = event match {
    case GroupCreated(group) => state = group
    case UserAdded(_, userId) => state = state.copy(members = state.members + userId)
    case UserRemoved(_, userId) => state = state.copy(members = state.members - userId)
    case MessageAdded(_, _, content) => state = state.copy(feed = content +: state.feed)
  }
}

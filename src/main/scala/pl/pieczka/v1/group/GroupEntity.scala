package pl.pieczka.v1.group

import akka.actor.Props
import akka.cluster.pubsub.DistributedPubSub
import pl.pieczka.common.PersistentEntity.Failure
import pl.pieczka.common.{EntityStateObject, Message, PersistentEntity, UserGroupAssociation}

case class GroupState(id: Int, members: Set[Int] = Set.empty, feed: Seq[Message] = Seq.empty) extends EntityStateObject[Int]

object GroupEntity {

  def props: Props = Props[GroupEntity]

  val entityType = "group"

  sealed trait GroupCommand extends PersistentEntity.EntityCommand {
    val groupId: Int

    override def entityId: String = groupId.toString
  }

  case class GetGroup(groupId: Int, userId: Int) extends GroupCommand


  case class AddUser(groupId: Int, userId: Int) extends GroupCommand

  case class RemoveUser(groupId: Int, userId: Int) extends GroupCommand

  case class AddMessage(groupId: Int, userId: Int, message: Message) extends GroupCommand

  case class GetMessages(groupId: Int, userId: Int) extends GroupCommand

  sealed trait GroupEvent extends PersistentEntity.EntityEvent

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

  var state = GroupState(id.toInt)

  override def additionalCommandHandling: Receive = {

    case GetGroup(groupId, userId) if !state.members.contains(userId) => sender() ! Left(NotMember(groupId, userId))

    case GetGroup(_, _) =>
      log.info("name {}", self.path.name)
      sender() ! Right(state)

    case AddMessage(groupId, userId, _) if !state.members.contains(userId) => sender() ! Left(NotMember(groupId, userId))

    case AddMessage(groupId, userId, message) =>
      val caller = sender()
      persist(MessageAdded(groupId, userId, message)) { evt =>
        log.debug("Message \"{}\" added to {} by user {}", evt.message, evt.groupId, evt.userId)
        handleEventAndMaybeSnapshot(evt)
        mediator ! Publish(s"group_${evt.groupId}", evt.message)
        caller ! Right(state)
      }

    case GetMessages(groupId, userId) if !state.members.contains(userId) => sender() ! Left(NotMember(groupId, userId))

    case GetMessages(_, _) =>
      sender() ! Right(state.feed)

    case UserGroupAssociation(userId, groupId) if groupId == id.toInt =>
      persist(UserAdded(groupId, userId)) { evt =>
        log.info("User {} joined group {}", evt.userId, evt.groupId)
        handleEventAndMaybeSnapshot(evt)
      }
  }

  override def handleEvent(event: PersistentEntity.EntityEvent): Unit = event match {
    case UserAdded(_, userId) => state = state.copy(members = state.members + userId)
    case UserRemoved(_, userId) => state = state.copy(members = state.members - userId)
    case MessageAdded(_, _, message) => state = state.copy(feed = message +: state.feed)
  }

  override def snapshotAfterCount: Option[Int] = Some(200)

}

package pl.pieczka.v1.user

import akka.actor.Props
import akka.cluster.pubsub.DistributedPubSub
import pl.pieczka.common.{Message, PersistentEntity}

object UserState {
  def empty: UserState = UserState(-1, "")
}

case class UserState(id: Int, name: String, groups: Set[Int] = Set.empty, feed: Seq[Message] = Seq.empty) {

  def isEmpty: Boolean = id < 0
}

object UserEntity {

  def props: Props = Props[UserEntity]

  val entityType = "user"

  sealed trait UserCommand extends PersistentEntity.EntityCommand {
    val userId: Int

    override def entityId: String = userId.toString
  }

  case class GetUser(userId: Int) extends UserCommand

  case class CreateUser(userId: Int, name: String) extends UserCommand

  case class AddGroup(userId: Int, groupId: Int) extends UserCommand

  case class RemoveGroup(userId: Int, groupId: Int) extends UserCommand

  sealed trait UserEvent extends PersistentEntity.EntityEvent

  case class UserCreated(user: UserState) extends UserEvent

  case class GroupAdded(userId: Int, groupId: Int) extends UserEvent

  case class GroupRemoved(userId: Int, groupId: Int) extends UserEvent

  case class MessagePublished(message: Message) extends UserEvent

  case class UserNotFound(userId: Int) {
    override def toString = s"User with id $userId not found"
  }

  case class UserAlreadyExists(userId: Int)

  type MaybeUser[+A] = Either[UserNotFound, A]

  type MaybeUserCreated[+A] = Either[UserAlreadyExists, A]

}

class UserEntity extends PersistentEntity {

  import UserEntity._
  import akka.cluster.pubsub.DistributedPubSubMediator.{Subscribe, SubscribeAck}

  val mediator = DistributedPubSub(context.system).mediator

  private var state = UserState.empty

  override def additionalCommandHandling: Receive = {

    case GetUser(id) if state.isEmpty => sender() ! Left(UserNotFound(id))

    case GetUser(_) => sender() ! Right(state)

    case CreateUser(id, name) if state.isEmpty =>
      val caller = sender()
      persist(UserCreated(UserState(id, name))) { evt =>
        log.info("New user registered {}/{}", evt.user.id, evt.user.name)
        handleEvent(evt)
        caller ! Right(state)
      }

    case CreateUser(userId, _) => sender() ! Left(UserAlreadyExists(userId))

    case AddGroup(userId, groupId) =>
      val caller = sender()
      persist(GroupAdded(userId, groupId)) { evt =>
        log.info("User {} joined group {}", evt.userId, evt.groupId)
        handleEvent(evt)
        // subscribe to the topic named "content"
        mediator ! Subscribe(s"group_${evt.groupId}", self)
        caller ! Right(state)
      }

    case RemoveGroup(userId, groupId) =>
      val caller = sender()
      persist(GroupRemoved(userId, groupId)) { evt =>
        log.info("User {} left group {}", evt.userId, evt.groupId)
        handleEvent(evt)
        caller ! Right(state)
      }

    case m: Message =>
      persist(MessagePublished(m)) { evt =>
        handleEvent(evt)
      }

    case SubscribeAck(Subscribe(topic, None, `self`)) =>
      log.info("User {} subscribed successfully to {}", state.id, topic)
  }

  override def handleEvent(event: PersistentEntity.EntityEvent): Unit = event match {
    case UserCreated(user) => state = user
    case GroupAdded(_, groupId) => state = state.copy(groups = state.groups + groupId)
    case GroupRemoved(_, groupId) => state = state.copy(groups = state.groups - groupId)
    case MessagePublished(m) => state = state.copy(feed = m +: state.feed)
  }
}

package pl.pieczka.v1.user

import akka.actor.Props
import pl.pieczka.common.PersistentEntity

object UserState {
  def empty: UserState = UserState(-1, "")
}

case class UserState(id: Int, name: String, groups: Seq[Int] = Seq.empty) {

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

  sealed trait UserEvent extends PersistentEntity.EntityEvent

  case class UserCreated(user: UserState) extends UserEvent

  case class UserNotFound(userId: Int) {
    override def toString = s"User with id $userId not found"
  }

  case class UserAlreadyExists(userId: Int)

  type MaybeUser[+A] = Either[UserNotFound, A]

  type MaybeUserCreated[+A] = Either[UserAlreadyExists, A]

}

class UserEntity extends PersistentEntity {

  import UserEntity._

  private var state = UserState.empty

  override def additionalCommandHandling: Receive = {

    case GetUser(id) if state.isEmpty => sender() ! Left(UserNotFound(id))

    case GetUser(_) => sender() ! Right(state)

    case CreateUser(id, name) if state.isEmpty =>
      val caller = sender()
      persist(UserCreated(UserState(id, name))) { evt =>
        log.info("New user registered {}/{}", evt.user.id, evt.user.name)
        state = evt.user
        caller ! Right(state)
      }

    case CreateUser(id, _) => sender() ! Left(UserAlreadyExists(id))

  }

  override def handleEvent(event: PersistentEntity.EntityEvent): Unit = event match {
    case UserCreated(user) =>
      state = user
  }
}

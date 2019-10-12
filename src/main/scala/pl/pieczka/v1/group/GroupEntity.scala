package pl.pieczka.v1.group

object GroupEntity {

  sealed trait GroupCommand

  case class JoinGroup(userId: Int)

  sealed trait GroupEvent

}

class GroupEntity {

}

package pl.pieczka.v1.group

import pl.pieczka.common.User

case class GroupInput(id: Int)

case class MessageInput(user: User, content: String)

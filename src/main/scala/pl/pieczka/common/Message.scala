package pl.pieczka.common

import java.util.Date

case class Message(id: String, groupId: Int, content: String, user: User, createdOn: Date = new Date())

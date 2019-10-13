package pl.pieczka.common

case class User(id: Int, name: String)

case class UserGroupAssociation(userId: Int, groupId: Int)

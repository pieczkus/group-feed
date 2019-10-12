package pl.pieczka.v1.user

import pl.pieczka.common.GroupFeedJsonProtocol
import pl.pieczka.v1.user.UsersManager.RegisterUser

trait UserJsonProtocol extends GroupFeedJsonProtocol {

  implicit val userStateFormat = jsonFormat3(UserState.apply)
  implicit val registerUserFormat = jsonFormat2(RegisterUser.apply)

}

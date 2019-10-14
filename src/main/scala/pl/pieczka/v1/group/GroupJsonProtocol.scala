package pl.pieczka.v1.group

import pl.pieczka.common.GroupFeedJsonProtocol

trait GroupJsonProtocol extends GroupFeedJsonProtocol {

  implicit val groupStateFormat = jsonFormat3(GroupState.apply)
  implicit val messageInputFormat = jsonFormat2(MessageInput.apply)


}

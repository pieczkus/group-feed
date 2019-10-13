package pl.pieczka.v1.group

import pl.pieczka.common.GroupFeedJsonProtocol

trait GroupJsonProtocol extends GroupFeedJsonProtocol {

  implicit val groupStateFormat = jsonFormat2(GroupState.apply)
  implicit val messageInputFormat = jsonFormat2(MessageInput.apply)


}

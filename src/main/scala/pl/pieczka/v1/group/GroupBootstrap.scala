package pl.pieczka.v1.group

import akka.actor.ActorSystem
import pl.pieczka.common.Bootstrap

class GroupBootstrap extends Bootstrap {

  def start(system: ActorSystem) = {
    import system.dispatcher
    val groupsManager = system.actorOf(GroupsManager.props, GroupsManager.name)

    List(new GroupRoutes(groupsManager)(system.dispatcher, system))
  }

}

package pl.pieczka.v1.user

import akka.actor.ActorSystem
import pl.pieczka.common.Bootstrap

class UserBootstrap extends Bootstrap {

  def start(system: ActorSystem) = {
    import system.dispatcher
    val usersManager = system.actorOf(UsersManager.props, UsersManager.name)
    List(new UserRoutes(usersManager))
  }

}

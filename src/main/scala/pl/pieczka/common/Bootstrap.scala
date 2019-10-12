package pl.pieczka.common

import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}

/**
 * Trait that defines a class that will boot up actors from within a specific services module
 */
trait Bootstrap {

  /**
   * Books up the actors for a service module and returns the service endpoints for that
   * module to be included in the Unfiltered server as plans
   *
   * @param system The actor system to boot actors into
   * @return a List of BookstorePlans to add as plans into the server
   */
  def start(system: ActorSystem): List[GroupFeedRoutesDefinition]

  /**
   * Starts up an actor as a singleton
   *
   * @param system      The system to use for starting the singleton
   * @param props       The props for the singleton actor
   * @param managerName The name to apply to the manager that manages the singleton
   * @return an ActorRef to the manager actor for that singleton
   */
  def startSingleton(system: ActorSystem, props: Props, managerName: String): ActorRef = {

    system.actorOf(
      ClusterSingletonManager.props(
        singletonProps = props,
        terminationMessage = PoisonPill,
        settings = ClusterSingletonManagerSettings(system)),
      managerName)
  }
}

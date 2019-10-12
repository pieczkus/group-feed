package pl.pieczka.common

import akka.actor.{ActorLogging, ActorSystem, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion
import akka.cluster.sharding.ShardRegion.Passivate
import akka.persistence.{PersistentActor, RecoveryCompleted, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}

import scala.concurrent.duration._

object PersistentEntity {

  trait EntityCommand {
    def entityId: String
  }

  trait EntityEvent {
  }

  case object StopEntity

  class PersistentEntityIdExtractor(maxShards: Int) {
    val extractEntityId: ShardRegion.ExtractEntityId = {
      case ec: EntityCommand => (ec.entityId, ec)
    }

    val extractShardId: ShardRegion.ExtractShardId = {
      case ec: EntityCommand =>
        (math.abs(ec.entityId.hashCode) % maxShards).toString
    }
  }

  object PersistentEntityIdExtractor {

    def apply(system: ActorSystem): PersistentEntityIdExtractor = {
      val maxShards = system.settings.config.getInt("maxShards")
      new PersistentEntityIdExtractor(maxShards)
    }
  }

}

abstract class PersistentEntity extends PersistentActor with ActorLogging {

  import PersistentEntity._

  val id: String = self.path.name
  val entityType: String = getClass.getSimpleName
  var eventsSinceLastSnapshot = 0

  override def persistenceId = id

  context.setReceiveTimeout(5.minutes)


  override def receiveCommand: Receive = standardCommandHandling orElse additionalCommandHandling

  private def standardCommandHandling: Receive = {

    //Have been idle too long, time to start the passivation process
    case ReceiveTimeout =>
      log.debug("{} entity with id {} is being passivated due to inactivity", entityType, id)
      context.parent ! Passivate(stopMessage = StopEntity)

    //Finishes the two part passivation process by stopping the entity
    case StopEntity =>
      log.debug("{} entity with id {} is now being stopped due to inactivity", entityType, id)
      context stop self

    case s: SaveSnapshotSuccess =>
      log.info("Successfully saved a new snapshot for entity {} and id {}", entityType, id)

    case f: SaveSnapshotFailure =>
      log.error(f.cause, "Failed to save a snapshot for entity {} and id {}, reason was {}", entityType)
  }

  def additionalCommandHandling: Receive


  override def receiveRecover = standardRecover orElse customRecover

  def standardRecover: Receive = {

    //For any entity event, just call handleEvent
    case ev: EntityEvent =>
      log.debug("Recovering persisted event: {}", ev)
      handleEvent(ev)
      eventsSinceLastSnapshot += 1

    case RecoveryCompleted =>
      log.debug("Recovery completed for {} entity with id {}", entityType, id)

  }

  def customRecover: Receive = PartialFunction.empty

  def handleEvent(event: EntityEvent): Unit
}

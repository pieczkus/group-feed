package pl.pieczka.common

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}

import scala.reflect.ClassTag

abstract class Aggregate[E <: PersistentEntity : ClassTag] extends Actor with ActorLogging {

  val idExtractor = PersistentEntity.PersistentEntityIdExtractor(context.system)

  val entityShardRegion: ActorRef = startEntity()

  def startEntity(): ActorRef = ClusterSharding(context.system).start(
    typeName = entityName,
    entityProps = entityProps,
    settings = ClusterShardingSettings(context.system),
    extractEntityId = idExtractor.extractEntityId,
    extractShardId = idExtractor.extractShardId
  )

  def entityProps: Props

  private def entityName = {
    val entityTag = implicitly[ClassTag[E]]
    entityTag.runtimeClass.getSimpleName
  }
}

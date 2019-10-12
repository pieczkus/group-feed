package pl.pieczka.v1.user

import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import pl.pieczka.common.PersistentEntity
import scala.concurrent.duration._

class UserEntitySpec extends TestKit(ActorSystem("UserSystemTest"))
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with ImplicitSender {

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  def joinCluster(): Unit = {
    Cluster.get(system).join(self.path.address)
    startSharding()
  }

  def startSharding(): Unit = {
    val idExtractor = PersistentEntity.PersistentEntityIdExtractor(system)
    ClusterSharding(system).start(
      typeName = UserEntity.entityType,
      entityProps = UserEntity.props,
      settings = ClusterShardingSettings(system),
      extractEntityId = idExtractor.extractEntityId,
      extractShardId = idExtractor.extractShardId)
  }

  "UserEntity" should {
    joinCluster()

    val userEntity = ClusterSharding(system).shardRegion(UserEntity.entityType)

    "result with not found if user not yet registered" in {
      //given
      val userId = 10

      //then
      userEntity ! UserEntity.GetUser(userId)

      //verify
      expectMsg(Left(UserEntity.UserNotRegistered(userId)))
    }

    "register new user" in {
      //given
      val userId = 10
      val name = "Bart"

      //then
      userEntity ! UserEntity.RegisterUser(userId, name)
      userEntity ! UserEntity.GetUser(userId)

      //verify
      expectMsg(Right(UserState(userId, name)))
      expectMsg(Right(UserState(userId, name)))
    }

    "reject already registered user" in {
      //given
      val userId = 10
      val name = "Bart"

      //then
      userEntity ! UserEntity.RegisterUser(userId, name)

      //verify
      expectMsg(Left(UserEntity.UserAlreadyRegistered(userId)))
    }
  }
}
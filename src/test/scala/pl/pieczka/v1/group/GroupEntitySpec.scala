package pl.pieczka.v1.group

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import pl.pieczka.common.PersistentEntity

class GroupEntitySpec extends TestKit(ActorSystem("GroupSystemTest"))
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
      typeName = GroupEntity.entityType,
      entityProps = GroupEntity.props,
      settings = ClusterShardingSettings(system),
      extractEntityId = idExtractor.extractEntityId,
      extractShardId = idExtractor.extractShardId)
  }

  "GroupEntity" should {
    joinCluster()

    val groupEntity = ClusterSharding(system).shardRegion(GroupEntity.entityType)

    "result with not found if group not yet created" in {
      //given
      val groupId = 10

      //then
      groupEntity ! GroupEntity.GetGroup(groupId)

      //verify
      expectMsg(Left(GroupEntity.GroupNotFound(groupId)))
    }

    "create new group" in {
      //given
      val groupId = 10

      //then
      groupEntity ! GroupEntity.CreateGroup(groupId)
      groupEntity ! GroupEntity.GetGroup(groupId)

      //verify
      expectMsg(Right(GroupState(groupId)))
      expectMsg(Right(GroupState(groupId)))
    }

    "accept new users" in {
      //given
      val groupId = 10
      val userId = 11;

      //then
      groupEntity ! GroupEntity.AddUser(groupId, userId)

      //verify
      expectMsg(Right(GroupState(groupId, members = Set(userId))))
    }

    "allow users to leave" in {
      //given
      val groupId = 10
      val userId = 11;
      val newUserId = 12;

      //then
      groupEntity ! GroupEntity.AddUser(groupId, newUserId)
      groupEntity ! GroupEntity.RemoveUser(groupId, newUserId)

      //verify
      expectMsg(Right(GroupState(groupId, members = Set(userId, newUserId))))
      expectMsg(Right(GroupState(groupId, members = Set(userId))))
    }

    "should not accept messages from strangers" in {
      //given
      val groupId = 10
      val userId = 11
      val strangerId = 111
      val message = "hello"

      //then
      groupEntity ! GroupEntity.AddMessage(groupId, strangerId, message)
      groupEntity ! GroupEntity.GetGroup(groupId)

      //verify
      expectMsg(Left(GroupEntity.NotMember(groupId, strangerId)))
      expectMsg(Right(GroupState(groupId, members = Set(userId))))
    }

    "should accept messages from members" in {
      //given
      val groupId = 10
      val userId = 11
      val message = "world"

      //then
      groupEntity ! GroupEntity.AddMessage(groupId, userId, message)

      //verify
      expectMsg(Right(GroupState(groupId, members = Set(userId), List(message))))
    }

    "feed should be returned in reverse order" in {
      //given
      val groupId = 10
      val userId = 11
      val message = "world"
      val anotherMessage = "hello"

      //then
      groupEntity ! GroupEntity.AddMessage(groupId, userId, anotherMessage)

      //verify
      expectMsg(Right(GroupState(groupId, members = Set(userId), List(anotherMessage, message))))
    }

  }

}
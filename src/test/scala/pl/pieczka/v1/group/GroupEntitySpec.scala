package pl.pieczka.v1.group

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, FeatureSpecLike, GivenWhenThen, Matchers}
import pl.pieczka.common.{Message, PersistentEntity, User, UserGroupAssociation}

class GroupEntitySpec extends TestKit(ActorSystem("GroupSystemTest"))
  with Matchers
  with FeatureSpecLike
  with BeforeAndAfterAll
  with ImplicitSender
  with GivenWhenThen {

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

  val userId = 11;
  val message = Message("1", 10, "world", User(userId, "Bart"))
  val anotherMessage = Message("2", 10, "hello", User(userId, "Bart"))

  joinCluster()

  val groupEntity = ClusterSharding(system).shardRegion(GroupEntity.entityType)
  val mediator = DistributedPubSub(system).mediator

  feature("Not created croup is not operational") {
    scenario("User is not able to get such group") {
      Given("Id of not yet created group")
      val groupId = 99

      When("Asked about group")
      groupEntity ! GroupEntity.GetGroup(groupId, userId)

      Then("Group not found should be returned")
      expectMsg(Left(GroupEntity.GroupNotFound(groupId)))
    }
  }

  feature("User can create new group") {

    scenario("Group not yet exists") {
      Given("Unique group id and user id")
      val groupId = 10

      When("Create group message is sent")
      groupEntity ! GroupEntity.CreateGroup(groupId, userId)

      Then("Group should be created with user as member")
      expectMsg(Right(GroupState(groupId)))
    }

    scenario("Group already exists") {
      Given("Id of existing group")
      val groupId = 10

      When("")
      groupEntity ! GroupEntity.CreateGroup(groupId, userId)

      Then("Group already exists should be returned")
      expectMsg(Left(GroupEntity.GroupAlreadyExists(groupId)))
    }
  }

  feature("Users can join group") {

    scenario("User is not yet member of a group") {
      Given("Id of existing group and new user id")
      val groupId = 10

      When("User joining notification is sent")
      mediator ! Publish("user-groups", UserGroupAssociation(userId, groupId))
      Thread.sleep(500)
      groupEntity ! GroupEntity.GetGroup(groupId, userId)

      Then("New user id is within members")
      expectMsg(Right(GroupState(groupId, members = Set(userId, userId))))
    }

    scenario("User is already member of a group") {
      Given("Id of existing group and user id of a group member")
      val groupId = 10
      val memberUserId = 100

      When("User joining notification is sent")
      mediator ! Publish("user-groups", UserGroupAssociation(memberUserId, groupId))
      Thread.sleep(500)
      groupEntity ! GroupEntity.GetGroup(groupId, userId)

      Then("User id is not duplicated")
      expectMsg(Right(GroupState(groupId, members = Set(userId, memberUserId))))
    }
  }

  feature("User posting messages in group") {

    scenario("User is not member of a group") {
      Given("Group id and id of a uses who is not a member")
      val groupId = 10
      val strangerId = 111

      When("Message is posted")
      groupEntity ! GroupEntity.AddMessage(groupId, strangerId, message)
      groupEntity ! GroupEntity.GetGroup(groupId, userId)

      Then("Message is not added into group feed")
      expectMsg(Left(GroupEntity.NotMember(groupId, strangerId)))
      expectMsg(Right(GroupState(groupId, members = Set(userId, 100))))
    }

    scenario("User is member of a group") {
      Given("Group id and id of a uses who is a member")
      val groupId = 10

      When("Message is posted")
      groupEntity ! GroupEntity.AddMessage(groupId, userId, message)

      Then("Message is not added into group feed")
      expectMsg(Right(GroupState(groupId, members = Set(userId, 100), Seq(message))))
    }

    scenario("Messages are ordered") {
      Given("Group id and id of a uses who is a member")
      val groupId = 10

      When("Another message is posted")
      groupEntity ! GroupEntity.AddMessage(groupId, userId, anotherMessage)

      Then("Message is not added into group feed")
      expectMsg(Right(GroupState(groupId, members = Set(userId, 100), Seq(anotherMessage, message))))
    }
  }
}

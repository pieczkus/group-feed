package pl.pieczka.v1.user

import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, FeatureSpecLike, GivenWhenThen, Matchers, WordSpecLike}
import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import pl.pieczka.common.{Message, PersistentEntity, User}

class UserEntitySpec extends TestKit(ActorSystem("UserSystemTest"))
  with FeatureSpecLike
  with Matchers
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
      typeName = UserEntity.entityType,
      entityProps = UserEntity.props,
      settings = ClusterShardingSettings(system),
      extractEntityId = idExtractor.extractEntityId,
      extractShardId = idExtractor.extractShardId)
  }

  joinCluster()

  val userEntity = ClusterSharding(system).shardRegion(UserEntity.entityType)
  val mediator = DistributedPubSub(system).mediator

  feature("User Entity") {

    scenario("result with not found if user not yet registered") {
      Given("User not yet registered")
      val userId = 10

      When("Asked abut user")
      userEntity ! UserEntity.GetUser(userId)

      Then("UserNotFound is returned")
      expectMsg(Left(UserEntity.UserNotFound(userId)))
    }

    scenario("register new user") {
      Given("User not yet registered")
      val userId = 10
      val name = "Bart"

      When("Requested to create user")
      userEntity ! UserEntity.CreateUser(userId, name)
      userEntity ! UserEntity.GetUser(userId)

      Then("User is created")
      expectMsg(Right(UserState(userId, name)))
      And("User state is returned")
      expectMsg(Right(UserState(userId, name)))
    }

    scenario("reject already registered user") {
      Given("User already registered")
      val userId = 10
      val name = "Bart"

      When("Requested to create user")
      userEntity ! UserEntity.CreateUser(userId, name)

      Then("UserAlreadyExists is returned")
      expectMsg(Left(UserEntity.UserAlreadyExists(userId)))
    }

    scenario("join group") {
      Given("Registered user")
      val userId = 10
      val name = "Bart"
      val groupId = 11

      When("Asked to join group")
      userEntity ! UserEntity.AddGroup(userId, groupId)

      Then("User state returned with group included")
      expectMsg(Right(UserState(userId, name, Set(groupId))))
    }

    scenario("add message to user feed") {
      Given("Registered user")
      val userId = 10
      val name = "Bart"
      val groupId = 11
      val message = Message("id", 1, "Hello", User(userId, name))

      When("Message is posted")
      mediator ! Publish(s"group_$groupId", message)
      Thread.sleep(500)
      userEntity ! UserEntity.GetUser(userId)

      Then("Message is included in user feed")
      expectMsg(Right(UserState(userId, name, Set(groupId), Seq(message))))
    }

    scenario("leave group") {
      Given("Registered user")
      val userId = 10
      val name = "Bart"
      val groupId = 11
      val message = Message("id", 1, "Hello", User(userId, name))

      When("Asked to leave group")
      userEntity ! UserEntity.RemoveGroup(userId, groupId)

      Then("User state returned without group")
      expectMsgPF() {
        case Right(UserState(10, "Bart", groups, feed)) =>
          feed.size shouldBe 1
          groups.size shouldBe 0
      }
    }
  }
}
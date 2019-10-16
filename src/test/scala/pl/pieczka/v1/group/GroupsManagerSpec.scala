package pl.pieczka.v1.group

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, FeatureSpecLike, GivenWhenThen, Matchers}
import pl.pieczka.common.{Message, PageParams, User}

class GroupsManagerSpec extends TestKit(ActorSystem("GroupsManagerSystemTest"))
  with FeatureSpecLike
  with Matchers
  with BeforeAndAfterAll
  with ImplicitSender
  with GivenWhenThen {

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  val entityProbe = TestProbe()
  val groupsManager = system.actorOf(Props(new GroupsManager() {
    override def startEntity(): ActorRef = entityProbe.ref
  }))

  feature("Groups Manager") {

    scenario("translates and forward find group by id command to region") {
      Given("Initialized group")
      val userId = 10
      val groupId = 11

      When("GetGroup message is received")
      groupsManager ! GroupsManager.FindGroupById(groupId, userId)

      Then("It's translated to GetGroup message and forwarded to region")
      entityProbe.expectMsg(GroupEntity.GetGroup(groupId, userId))
    }

    scenario("translate and forward post message command") {
      Given("Group without any members")
      val groupId = 11
      val userId = 11
      val message = MessageInput(User(userId, "Bart"), "hello world")

      When("PostMessage message is received")
      groupsManager ! GroupsManager.PostMessage(groupId, userId, message)

      Then("It's translated to AddMessage and forwarded to region")
      entityProbe.expectMsgPF() {
        case GroupEntity.AddMessage(11, 11, Message(_, 11, message.content, User(11, "Bart"), _)) =>
      }
    }

    scenario("return feed when user is member of a group") {
      Given("Group with user within members")
      val groupId = 11
      val userId = 11
      val messages = Seq(Message("id", 1, "Hello", User(userId, "Bart")))

      When("GetFeed message is received")
      groupsManager ! GroupsManager.GetFeed(groupId, userId, PageParams())

      Then("Group is fetched by it's it")
      entityProbe.expectMsg(GroupEntity.GetGroup(groupId, userId))
      entityProbe.reply(Right(GroupState(groupId, Set(userId), messages)))
      And("Feed from group is returned")
      expectMsg(Right(messages))
    }

    scenario("do not return feed when user is not member of a group") {
      Given("Group without members")
      val groupId = 11
      val userId = 11
      val messages = Seq(Message("id", 1, "Hello", User(userId, "Bart")))

      When("GetFeed message is received")
      groupsManager ! GroupsManager.GetFeed(groupId, userId, PageParams())

      Then("Group is fetched by it's it")
      entityProbe.expectMsg(GroupEntity.GetGroup(groupId, userId))
      entityProbe.reply(Right(GroupState(groupId, Set(10), messages)))
      And("NotMember is returned")
      expectMsg(Left(GroupEntity.NotMember(groupId, userId)))
    }

  }

}

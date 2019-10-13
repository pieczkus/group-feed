package pl.pieczka.v1.group

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import pl.pieczka.common.{Message, User}

class GroupsManagerSpec extends TestKit(ActorSystem("GroupsManagerSystemTest"))
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with ImplicitSender {

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  val entityProbe = TestProbe()
  val groupsManager = system.actorOf(Props(new GroupsManager() {
    override def startEntity(): ActorRef = entityProbe.ref
  }))

  "GroupsManager" should {

    "translate and forward find group by id command" in {
      //given
      val groupId = 11

      //then
      groupsManager ! GroupsManager.FindGroupById(groupId)

      //verify
      entityProbe.expectMsg(GroupEntity.GetGroup(groupId))
    }

    "translate and forward post message command" in {
      //given
      val groupId = 11
      val userId = 11
      val message = MessageInput(User(userId, "Bart"), "hello world")

      //then
      groupsManager ! GroupsManager.PostMessage(groupId, userId, message)

      //verify
      entityProbe.expectMsgPF() {
        case GroupEntity.AddMessage(11, 11, Message(_, 11, message.content, User(11, "Bart"), _)) =>
      }
    }

    "translate and forward get feed command" in {
      //given
      val groupId = 11
      val userId = 11

      //then
      groupsManager ! GroupsManager.GetFeed(groupId, userId)

      //verify
      entityProbe.expectMsg(GroupEntity.GetMessages(groupId, userId))
    }

  }

}

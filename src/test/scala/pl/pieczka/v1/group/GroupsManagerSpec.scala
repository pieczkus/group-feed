package pl.pieczka.v1.group

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

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

    "translate and forward join group command" in {
      //given
      val groupId = 11
      val userId = 11

      //then
      groupsManager ! GroupsManager.JoinGroup(groupId, userId)

      //verify
      entityProbe.expectMsg(GroupEntity.AddUser(groupId, userId))
    }

    "translate and forward leave group command" in {
      //given
      val groupId = 11
      val userId = 11

      //then
      groupsManager ! GroupsManager.LeaveGroup(groupId, userId)

      //verify
      entityProbe.expectMsg(GroupEntity.RemoveUser(groupId, userId))
    }

    "translate and forward post message command" in {
      //given
      val groupId = 11
      val userId = 11
      val message = "Hello world"

      //then
      groupsManager ! GroupsManager.PostMessage(groupId, userId, message)

      //verify
      entityProbe.expectMsg(GroupEntity.AddMessage(groupId, userId, message))
    }

  }

}

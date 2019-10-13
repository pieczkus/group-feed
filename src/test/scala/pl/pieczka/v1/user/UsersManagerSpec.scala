package pl.pieczka.v1.user

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class UsersManagerSpec extends TestKit(ActorSystem("UsersManagerSystemTest"))
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with ImplicitSender {

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  val entityProbe = TestProbe()
  val usersManager = system.actorOf(Props(new UsersManager() {
    override def startEntity(): ActorRef = entityProbe.ref
  }))

  "UsersManager" should {

    "translate and forward user create command" in {
      //given
      val userId = 11
      val name = "Frank"

      //then
      usersManager ! UsersManager.RegisterUser(userId, name)

      //verify
      entityProbe.expectMsg(UserEntity.CreateUser(userId, name))
    }

    "translate and forward find by id command" in {
      //given
      val userId = 11

      //then
      usersManager ! UsersManager.FindUserById(userId)

      //verify
      entityProbe.expectMsg(UserEntity.GetUser(userId))
    }

    "translate and forward join group command" in {
      //given
      val userId = 11
      val groupId = 12

      //then
      usersManager ! UsersManager.JoinGroup(userId, groupId)

      //verify
      entityProbe.expectMsg(UserEntity.AddGroup(userId, groupId))
    }

    "translate and forward leave group command" in {
      //given
      val userId = 11
      val groupId = 12

      //then
      usersManager ! UsersManager.LeaveGroup(userId, groupId)

      //verify
      entityProbe.expectMsg(UserEntity.RemoveGroup(userId, groupId))
    }

    "translate and forward find by token command" in {
      //given
      val userId = 11
      val token = "11"

      //then
      usersManager ! UsersManager.FindUserByToken(token)

      //verify
      entityProbe.expectMsg(UserEntity.GetUser(userId))
    }

  }
}

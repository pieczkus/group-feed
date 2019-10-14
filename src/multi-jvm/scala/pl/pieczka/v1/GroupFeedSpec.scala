package pl.pieczka.v1

import java.io.File

import akka.cluster.Cluster
import akka.cluster.sharding.ClusterSharding
import akka.remote.testconductor.RoleName
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import akka.testkit.ImplicitSender
import com.typesafe.config.ConfigFactory
import pl.pieczka.common.{Message, User}
import pl.pieczka.v1.group.{GroupBootstrap, GroupEntity, GroupState}
import pl.pieczka.v1.user.{UserBootstrap, UserEntity, UserState}
import org.apache.commons.io.FileUtils

import scala.concurrent.duration._

object GroupFeedSpec extends MultiNodeConfig {

  val node1 = role("node1")
  val node2 = role("node2")
  //  val node3 = role("node3")

  nodeConfig(node1)(ConfigFactory.parseString(
    """
      |akka.persistence.journal.leveldb.dir = "target/journal-A"
    """.stripMargin))

  nodeConfig(node2)(ConfigFactory.parseString(
    """
      |akka.persistence.journal.leveldb.dir = "target/journal-B"
    """.stripMargin))

  //  nodeConfig(node3)(ConfigFactory.parseString(
  //    """
  //      |akka.persistence.journal.leveldb.dir = "target/journal-C"
  //    """.stripMargin))

  commonConfig(ConfigFactory.parseString(
    """
      |maxShards=5
      |akka.actor.warn-about-java-serializer-usage=false
      |akka.loglevel=INFO
      |akka.actor.provider = cluster
      |akka.remote.artery.enabled = on
      |akka.coordinated-shutdown.run-by-jvm-shutdown-hook = off
      |akka.coordinated-shutdown.terminate-actor-system = off
      |akka.cluster.run-coordinated-shutdown-when-down = off
      |akka.persistence.journal.plugin = "akka.persistence.journal.leveldb"
    """.stripMargin))
}

class GroupFeedSpecMultiJvmNode1 extends GroupFeedSpec

class GroupFeedSpecMultiJvmNode2 extends GroupFeedSpec

//class GroupFeedSpecMultiJvmNode3 extends GroupFeedSpec

class GroupFeedSpec extends MultiNodeSpec(GroupFeedSpec)
  with STMultiNodeSpec with ImplicitSender {

  import GroupFeedSpec._

  override def initialParticipants: Int = roles.size

  val storageLocations = List(
    "akka.persistence.journal.leveldb.dir",
    "akka.persistence.journal.leveldb-shared.store.dir",
    "akka.persistence.snapshot-store.local.dir").map(s => new File(system.settings.config.getString(s)))

  override protected def afterTermination() {
    runOn(node1, node2) {
      storageLocations.foreach(dir => FileUtils.deleteDirectory(dir))
    }
  }

  def join(from: RoleName, to: RoleName): Unit = {
    runOn(from) {
      Cluster(system) join node(to).address
      bootUp()
    }
    enterBarrier(from.name + "-joined")
  }

  def bootUp(): Unit = {
    List(new UserBootstrap(), new GroupBootstrap())
      .flatMap(_.start(system))
  }

  "Group Feed app" must {

    "boot up" in within(15.seconds) {
      join(node1, node1)
      join(node2, node1)
    }
    enterBarrier("booted-up")


    "register new user" in {
      runOn(node1) {
        val userRegion = ClusterSharding(system).shardRegion("UserEntity")
        userRegion ! UserEntity.CreateUser(1, "Bart")
        expectMsg(Right(UserState(1, "Bart")))
      }

      enterBarrier("user-created")

      runOn(node2) {
        val userRegion = ClusterSharding(system).shardRegion("UserEntity")
        userRegion ! UserEntity.GetUser(1)
        expectMsg(Right(UserState(1, "Bart")))

        val groupRegion = ClusterSharding(system).shardRegion("GroupEntity")
        groupRegion ! GroupEntity.GetGroup(99, 1)
        expectMsg(Left(GroupEntity.NotMember(99, 1)))
      }

      enterBarrier("after-user-created")
    }

    "allow user to join group" in {
      runOn(node1) {
        val userRegion = ClusterSharding(system).shardRegion("UserEntity")
        userRegion ! UserEntity.AddGroup(1, 99)
        expectMsg(Right(UserState(1, "Bart", Set(99))))

        userRegion ! UserEntity.GetUser(1)
        expectMsg(Right(UserState(1, "Bart", Set(99))))
      }

      enterBarrier("user-joined-group")

      runOn(node2) {
        val userRegion = ClusterSharding(system).shardRegion("UserEntity")
        userRegion ! UserEntity.GetUser(1)
        expectMsg(Right(UserState(1, "Bart", Set(99), Seq.empty[Message])))

        val groupRegion = ClusterSharding(system).shardRegion("GroupEntity")
        groupRegion ! GroupEntity.GetGroup(99, 1)
        expectMsg(Right(GroupState(99, Set(1))))
      }
      enterBarrier("after-user-joined-group")
    }

    "post message to group and populate user feed" in {
      runOn(node1) {
        val groupRegion = ClusterSharding(system).shardRegion("GroupEntity")
        groupRegion ! GroupEntity.AddMessage(99, 1, Message("m1", 99, "Hello", User(1, "Bart")))
        expectMsgPF() {
          case Right(GroupState(99, _, feed))=>
            feed.size shouldBe 1
        }
      }

      runOn(node2) {
        val groupRegion = ClusterSharding(system).shardRegion("GroupEntity")
        groupRegion ! GroupEntity.AddMessage(99, 1, Message("m2", 99, "World", User(1, "Bart")))
        expectMsgPF() {
          case Right(GroupState(99, _, feed))=>
            feed.size shouldBe 2
        }
      }
      enterBarrier("messages-posted")

      runOn(node2) {
        val groupRegion = ClusterSharding(system).shardRegion("GroupEntity")
        groupRegion ! GroupEntity.GetGroup(99, 1)
        expectMsgPF() {
          case Right(GroupState(99, _, feed))=>
            feed.size shouldBe 2
        }
      }

      enterBarrier("verified-group-feed")

      runOn(node1) {
        val userRegion = ClusterSharding(system).shardRegion("UserEntity")
        userRegion ! UserEntity.GetUser(1)
        expectMsgPF() {
          case r@Right(UserState(1, "Bart", _, feed)) =>
            feed.size shouldBe 2
        }
      }

      enterBarrier("verified-feed")
    }

  }
}
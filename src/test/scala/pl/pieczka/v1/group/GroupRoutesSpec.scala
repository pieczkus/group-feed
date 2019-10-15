package pl.pieczka.v1.group

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ContentTypes, HttpRequest, MessageEntity, StatusCodes}
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpec}
import pl.pieczka.v1.user.{UserBootstrap, UserJsonProtocol}

import scala.concurrent.duration._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import pl.pieczka.common.{ApiResponse, User}

class GroupRoutesSpec extends WordSpec with Matchers with ScalaFutures with ScalatestRouteTest with UserJsonProtocol with GroupJsonProtocol {

  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(5.seconds)

  Cluster.get(system).join(system.deadLetters.path.address)

  val routes = List(new UserBootstrap(), new GroupBootstrap())
    .flatMap(_.start(system)).
    map(_.routes)
  val definedRoutes = routes.reduce(_ ~ _)

  val finalRoutes = pathPrefix("api")(definedRoutes)

  "Group service" should {

    "return 200 and user state JSON when registering (POST /api/user)" in {
      val user = User(1, "Bart")
      val userEntity = Marshal(user).to[MessageEntity].futureValue

      val request = Post("/api/user").withEntity(userEntity)

      request ~> finalRoutes ~> check {
        status should ===(StatusCodes.OK)
        contentType should ===(ContentTypes.`application/json`)
        entityAs[String] should ===("""{"response":{"feed":[],"groups":[],"id":1,"name":"Bart"}}""")
      }
    }

    "return 400 when checking user feed without authorization (GET /api/group/1)" in {
      val request = HttpRequest(uri = "/api/group/1")
      request ~> Route.seal(finalRoutes) ~> check {
        status should ===(StatusCodes.BadRequest)
        responseAs[String] shouldEqual """Request is missing required HTTP header 'X-Token'"""
      }
    }

    "return 403 when checking user feed with invalid X-Token (GET api/group/1)" in {
      val request = Get("/api/group/1").withHeaders(Seq(RawHeader("X-Token", "10")))
      request ~> Route.seal(finalRoutes) ~> check {
        status should ===(StatusCodes.Forbidden)
        responseAs[String] shouldEqual """The supplied authentication is not authorized to access this resource"""
      }
    }

    "return 404 not found if no present (GET /api/group/1)" in {
      val request = HttpRequest(uri = "/api/group/1").withHeaders(Seq(RawHeader("X-Token", "1")))

      request ~> finalRoutes ~> check {
        status should ===(StatusCodes.NotFound)
      }
    }

    "return 200 and group state JSON when creating new group (POST /api/group)" in {
      val group = GroupInput(1)
      val groupEntity = Marshal(group).to[MessageEntity].futureValue

      val request = Post("/api/group").withEntity(groupEntity).withHeaders(Seq(RawHeader("X-Token", "1")))

      request ~> finalRoutes ~> check {
        status should ===(StatusCodes.OK)
        contentType should ===(ContentTypes.`application/json`)
        entityAs[String] should ===("""{"response":{"feed":[],"id":1,"members":[1]}}""")
      }
    }

    "return 200 and group feed JSON  (POST /api/group/1/feed)" in {
      val request = Get("/api/group/1/feed").withHeaders(Seq(RawHeader("X-Token", "1")))

      request ~> finalRoutes ~> check {
        status should ===(StatusCodes.OK)
        contentType should ===(ContentTypes.`application/json`)
        entityAs[String] should ===("""{"response":[]}""")
      }
    }

    "return 200 and group state JSON when posting message (POST /api/group/1/feed)" in {
      val user = User(1, "Bart")
      val messageInput = MessageInput(user, "hello")
      val messageEntity = Marshal(messageInput).to[MessageEntity].futureValue

      val request = Post("/api/group/1/feed").withHeaders(Seq(RawHeader("X-Token", "1"))).withEntity(messageEntity)

      request ~> finalRoutes ~> check {
        status should ===(StatusCodes.OK)
        contentType should ===(ContentTypes.`application/json`)
      }
    }

  }

}

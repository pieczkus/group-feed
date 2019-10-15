package pl.pieczka.v1.user

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpec}
import akka.http.scaladsl.server.Directives._
import pl.pieczka.common.User
import akka.http.scaladsl.model.headers.{RawHeader}

import scala.concurrent.duration._

class UserRoutesSpec extends WordSpec with Matchers with ScalaFutures with ScalatestRouteTest with UserJsonProtocol  {

  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(5.seconds)

  Cluster.get(system).join(system.deadLetters.path.address)

  val routes = new UserBootstrap().start(system).map(_.routes)

  val definedRoutes = routes.reduce(_ ~ _)

  val finalRoutes = pathPrefix("api")(definedRoutes)



  "User service" should {

    "return 404 not found if no present (GET /api/user/1)" in {
      val request = HttpRequest(uri = "/api/user/1")

      request ~> finalRoutes ~> check {
        status should ===(StatusCodes.NotFound)
      }
    }

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

    "return 400 when checking user feed without authorization (GET /api/user/1/feed)" in {
      val request = HttpRequest(uri = "/api/user/feed")
      request ~> Route.seal(finalRoutes) ~> check {
        status should ===(StatusCodes.BadRequest)
        responseAs[String] shouldEqual """Request is missing required HTTP header 'X-Token'"""
      }
    }

    "return 403 when checking user feed with invalid X-Token (GET /api/user/feed)" in {
      val request = Get("/api/user/feed").withHeaders(Seq(RawHeader("X-Token", "10")))
      request ~> Route.seal(finalRoutes) ~> check {
        status should ===(StatusCodes.Forbidden)
        responseAs[String] shouldEqual """The supplied authentication is not authorized to access this resource"""
      }
    }

    "return 200 and actual feed when authenticated (GET /api/user/feed)" in {
      val request = Get("/api/user/feed").withHeaders(Seq(RawHeader("X-Token", "1")))
      request ~> Route.seal(finalRoutes) ~> check {
        status should ===(StatusCodes.OK)
        entityAs[String] should ===("""{"response":[]}""")
      }
    }

    "return 200 and user state after joining group (POST /api/user/group)" in {
      val joinGroupInput = JoinGroupInput(99)
      val joinGroupEntity = Marshal(joinGroupInput).to[MessageEntity].futureValue

      val request = Post("/api/user/group").withHeaders(Seq(RawHeader("X-Token", "1"))).withEntity(joinGroupEntity)
      request ~> Route.seal(finalRoutes) ~> check {
        status should ===(StatusCodes.OK)
        entityAs[String] should ===("""{"response":{"feed":[],"groups":[99],"id":1,"name":"Bart"}}""")
      }
    }

    "return 200 and user state after leaving group (DELETE /api/user/group/99)" in {
      val request = Delete("/api/user/group/99").withHeaders(Seq(RawHeader("X-Token", "1")))
      request ~> Route.seal(finalRoutes) ~> check {
        status should ===(StatusCodes.OK)
        entityAs[String] should ===("""{"response":{"feed":[],"groups":[],"id":1,"name":"Bart"}}""")
      }
    }

  }

}

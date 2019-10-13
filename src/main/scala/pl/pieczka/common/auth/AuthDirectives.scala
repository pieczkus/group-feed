package pl.pieczka.common.auth

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{AuthorizationFailedRejection, Directive1}

import scala.concurrent.Future
import scala.util.{Failure, Success}


trait AuthDirectives {

  val tokenHeaderName = "X-Token"

  def authenticate: Directive1[Int] = {
    headerValueByName(tokenHeaderName).flatMap { token =>
      onComplete(verifyToken(token)).flatMap {
        case Success(Some(userId)) => provide(userId)
        case Failure(ex) => ex.printStackTrace(); reject(AuthorizationFailedRejection)
      }
    }
  }

  def verifyToken(token: String): Future[Option[Int]]
}

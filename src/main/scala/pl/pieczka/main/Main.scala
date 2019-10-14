package pl.pieczka.main

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import pl.pieczka.v1.user.UserBootstrap
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.Sink
import pl.pieczka.v1.group.GroupBootstrap
import scala.concurrent.duration._

import scala.concurrent.Await

object Main extends App {

  val conf = ConfigFactory.load.getConfig("groupfeed").resolve()

  implicit val system = ActorSystem("GroupFeedSystem", conf)
  implicit val mater = ActorMaterializer()
  val log = Logging(system.eventStream, "GroupFeed")

  import system.dispatcher

  val routes = List(new UserBootstrap(), new GroupBootstrap())
    .flatMap(_.start(system)).
    map(_.routes)
  val definedRoutes = routes.reduce(_ ~ _)

  val finalRoutes = pathPrefix("api")(definedRoutes)

  val serverSource = Http().bind(interface = "0.0.0.0", port = conf.getInt("httpPort"))
  val sink = Sink.foreach[Http.IncomingConnection](_.handleWith(finalRoutes))
  serverSource.to(sink).run

  scala.sys.addShutdownHook {
    system.terminate()
    Await.result(system.whenTerminated, 30.seconds)
  }

}

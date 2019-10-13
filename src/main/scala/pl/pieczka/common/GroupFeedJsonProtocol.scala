package pl.pieczka.common

import java.util.Date

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, JsNumber, JsValue, JsonFormat, deserializationError}

trait GroupFeedJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {

  implicit object DateFormat extends JsonFormat[Date] {
    def write(date: Date): JsValue = JsNumber(date.getTime)

    def read(json: JsValue): Date = json match {
      case JsNumber(epoch) => new Date(epoch.toLong)
      case unknown => deserializationError(s"Expected JsNumber, got $unknown")
    }
  }

  implicit val userInputFormat = jsonFormat2(User.apply)

}

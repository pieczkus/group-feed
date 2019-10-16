package pl.pieczka.common

import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives._


case class PageParams(pageNumber: Option[Int] = None, pageSize: Option[Int] = None) {
  require(pageNumber.forall(size => size > 0), "page number must be greater then 0")
  require(pageSize.forall(size => size > 0 && size < 1000), "page size must be greater then 0 and less then 1000")

  private val DefaultPageNumber = 1
  private val DefaultPageSize = 2

  val limit: Int = pageSize.getOrElse(DefaultPageSize)
  val skip: Int = (pageNumber.getOrElse(DefaultPageNumber) - 1) * limit

}

trait PagingDirectives {

  def pageParams: Directive1[PageParams] =
    parameters((Symbol("pageNumber").as[Int].?, Symbol("pageSize").as[Int].?)).as(PageParams)

}

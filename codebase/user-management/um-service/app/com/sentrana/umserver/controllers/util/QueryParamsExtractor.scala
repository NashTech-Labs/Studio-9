package com.sentrana.umserver.controllers.util

import play.api.mvc.{ AnyContent, Request }

/**
 * Created by Alexander on 23.05.2016.
 */
trait QueryParamsExtractor {
  def getLimitOffset(req: Request[AnyContent]): (Int, Int) = {
    val limit = req.getQueryString("limit").map(_.toInt).getOrElse(10)
    val offset = req.getQueryString("offset").map(_.toInt).getOrElse(0)
    (limit, offset)
  }
}

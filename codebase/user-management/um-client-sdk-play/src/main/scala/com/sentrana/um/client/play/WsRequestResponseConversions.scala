package com.sentrana.um.client.play

import play.api.libs.ws.{ WSClient, WSRequest, WSResponse }
import play.api.mvc.BodyParsers.parse
import play.api.mvc.{ RawBuffer, Request, Result, Results }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Created by Alexander on 18.05.2016.
 */
private[play] trait WsRequestResponseConversions {
  def wsClient: WSClient

  protected def buildWsRequest(request: Request[RawBuffer], url: String): WSRequest = {
    wsClient.url(url).withHeaders(request.headers.headers: _*).
      withQueryString(getQueryString(request.queryString): _*)
  }

  protected def genericWsResponseToResult(respFuture: Future[WSResponse]): Future[Result] = {
    respFuture.map(resp => Results.Status(resp.status)(resp.bodyAsBytes))
  }

  protected def getRawContent(buffer: RawBuffer): Array[Byte] = {
    buffer.asBytes(parse.DefaultMaxTextLength.toLong).getOrElse(Array[Byte]())
  }

  private def getQueryString(queryString: Map[String, Seq[String]]): Seq[(String, String)] = {
    val queryStringMap = for {
      (key, valueSeq) <- queryString
      value <- valueSeq
    } yield (key, value)

    queryStringMap.toSeq
  }
}

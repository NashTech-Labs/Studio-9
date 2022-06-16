package com.sentrana.umserver.dtos

import play.api.libs.json.Json

/**
 * Created by Paul Lysak on 18.05.16.
 */
case class SamlStarter(links: Map[String, String])

object SamlStarter {
  implicit val writes = Json.writes[SamlStarter]
}


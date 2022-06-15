package aries.domain.rest.status

import aries.domain.rest.HttpContract

final case class About(serviceName: String, currentUtc: String, version: String, builtAt: String) extends HttpContract

final case class Status(serviceName: String, uptime: String) extends HttpContract

final case class HealthCheckResponse(ok: Boolean) extends HttpContract


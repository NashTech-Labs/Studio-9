package taurus.domain.rest.status

import taurus.domain.rest.HttpContract

final case class About(serviceName: String, currentUtc: String, version: String, builtAt: String) extends HttpContract

final case class Status(serviceName: String, uptime: String) extends HttpContract

final case class HealthCheckResponse(ok: Boolean) extends HttpContract


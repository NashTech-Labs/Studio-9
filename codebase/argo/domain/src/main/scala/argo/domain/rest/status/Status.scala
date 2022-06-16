package argo.domain.rest.status

import argo.domain.rest.HttpContract

case class About(serviceName: String, currentUtc: String, version: String, builtAt: String) extends HttpContract

case class Status(serviceName: String, uptime: String) extends HttpContract

case class HealthCheckResponse(ok: Boolean) extends HttpContract


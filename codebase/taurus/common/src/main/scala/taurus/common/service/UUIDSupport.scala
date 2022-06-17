package taurus.common.service

import java.util.UUID

trait UUIDSupport { self: Service =>

  def randomUUID(): UUID = {
    java.util.UUID.randomUUID
  }
}

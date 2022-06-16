package argo.common.service

import java.time.temporal.ChronoUnit
import java.util.Date

trait DateSupport extends DateImplicits { self: Service =>

  def currentDate(): Date = {
    new Date()
  }
}

trait DateImplicits {
  implicit class ExtendedDate(date: Date) {
    def withoutMillis(): Date = {
      Date.from(date.toInstant().truncatedTo(ChronoUnit.SECONDS))
    }
  }
}

object DateImplicits extends DateImplicits
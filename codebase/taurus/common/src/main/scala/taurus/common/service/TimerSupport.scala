package taurus.common.service

import akka.actor.Cancellable
import taurus.common.service.TimerSupport.TimerMessage

import scala.concurrent.duration.FiniteDuration

trait TimerSupport { service: Service =>

  private var timer: Cancellable = _
  val timerValue: FiniteDuration

  def onTimerTrigger(): Unit

  def startTimer(): Unit = {
    if (Option(timer).isDefined) {
      throw new IllegalStateException("Timer was already started")
    }
    timer = context.system.scheduler.scheduleOnce(timerValue)(self ! TimerMessage)(context.dispatcher)
  }

  def stopTimer(): Unit = {
    val t = Option(timer).getOrElse(throw new IllegalStateException("Timer was not started"))
    t.cancel()
    timer = null
  }

  def restartTimer(): Unit = {
    stopTimer()
    startTimer()
  }

  override def receive: Receive = {
    case TimerMessage =>
      onTimerTrigger()
  }
}

object TimerSupport {
  case object TimerMessage
}

package orion.common.service

import akka.actor.{ Actor, ActorLogging }

/**
 * Base trait for Services
 */
trait Service extends Actor with ActorLogging

/**
 * A convenience trait for an actor companion object to extend to provide names.
 */
trait NamedActor {
  val Name: String
}
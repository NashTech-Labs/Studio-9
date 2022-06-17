package argo.common.rest.routes

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{ Directives, Route }
import akka.pattern.ask
import akka.util.Timeout
import argo.common.json4s.Json4sSupport
import argo.common.rest.marshalling.Json4sHttpSupport
import argo.domain.rest.HttpContract
import argo.domain.service._

import scala.reflect.ClassTag

trait CRUDRoutes {
  self: Directives with Json4sSupport with Json4sHttpSupport with ResponseHandling with ImplicitTranslations =>

  implicit val timeout: Timeout

  def create[In <: HttpContract, D <: DomainObject, E <: DomainEntity[_]: ClassTag, Out <: HttpContract](commandService: ActorRef)(implicit requestTranslator: Translator[In, D], responseTranslator: Translator[E, Out], m: Manifest[In]): Route = {
    (pathEnd & post & entity(as[In])) { contract =>
      val msg = CreateEntity(contract)
      onSuccess((commandService ? msg).mapTo[E]) { entity =>
        respond(StatusCodes.Created, entity)
      }
    }
  }

  def retrieve[E <: DomainEntity[_]: ClassTag, Out <: HttpContract](queryService: ActorRef)(implicit responseTranslator: Translator[E, Out]): Route = {
    (path(JavaUUID) & get) { id =>
      onSuccess((queryService ? RetrieveEntity(id)).mapTo[Option[E]]) {
        case Some(entity) => respond(entity)
        case None         => respond(StatusCodes.NotFound)
      }
    }
  }

  def update[In <: HttpContract, Data <: DomainObject, E <: DomainEntity[_]: ClassTag, Out <: HttpContract](commandService: ActorRef)(implicit requestTranslator: Translator[In, Data], responseTranslator: Translator[E, Out], m: Manifest[In]): Route = {
    (path(JavaUUID) & put & entity(as[In])) { (id, contract) =>
      val msg = UpdateEntity(id, contract)
      onSuccess((commandService ? msg).mapTo[Option[E]]) {
        case Some(entity) => respond(entity)
        case None         => respond(StatusCodes.NotFound)
      }
    }
  }

  def remove[E <: DomainEntity[_]: ClassTag, Out <: HttpContract](commandService: ActorRef)(implicit responseTranslator: Translator[E, Out]): Route = {
    (path(JavaUUID) & delete) { id =>
      onSuccess((commandService ? DeleteEntity(id)).mapTo[Option[E]]) {
        case Some(entity) => respond(entity)
        case None         => respond(StatusCodes.NotFound)
      }
    }
  }

  def list[E <: DomainEntity[_]: ClassTag, Out <: HttpContract](queryService: ActorRef)(implicit responseTranslator: Translator[E, Out]): Route = {
    (pathEnd & get) {
      onSuccess((queryService ? ListEntities).mapTo[Seq[E]]) { entities =>
        respond(entities)
      }
    }
  }

  def crudRoutes[In <: HttpContract, D <: DomainObject, E <: DomainEntity[_]: ClassTag, Out <: HttpContract](commandService: ActorRef, queryService: ActorRef)(implicit requestTranslator: Translator[In, D], responseTranslator: Translator[E, Out], m: Manifest[In]): Route = {
    create[In, D, E, Out](commandService) ~
      retrieve[E, Out](queryService) ~
      update[In, D, E, Out](commandService) ~
      remove[E, Out](commandService) ~
      list(queryService)
  }

}
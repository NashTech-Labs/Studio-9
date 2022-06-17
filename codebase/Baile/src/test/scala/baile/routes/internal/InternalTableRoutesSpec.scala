package baile.routes.internal

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.server.Route
import baile.daocommons.WithId
import baile.routes.RoutesSpec
import baile.services.table.TableService
import baile.services.table.TableService.InternalTableServiceError
import baile.services.table.util.TestData.TableEntity
import cortex.api.ErrorResponse
import cortex.api.baile.TableReferenceResponse
import org.mockito.Mockito._

class InternalTableRoutesSpec extends RoutesSpec {

  private val service = mock[TableService]
  private val validCreds = BasicHttpCredentials(
    conf.getString("private-http.username"),
    conf.getString("private-http.password")
  )
  val routes: Route = new InternalTableRoutes(conf, service).routes
  val table = WithId(TableEntity, "id")

  "GET /tables-references" should {

    "return error response with Unauthorized status if user don't have access" in {
      when(service.list(table.entity.ownerId, Seq(table.id))) thenReturn
        future(Left(InternalTableServiceError.AccessDenied))

      Get(s"/tables-references?userId=${ table.entity.ownerId }&tableIds=${ table.id }")
        .withCredentials(validCreds)
        .check {
          status shouldBe StatusCodes.Unauthorized
          responseAs[ErrorResponse].code shouldBe TableReferenceResponse.Errors.Table2.code
      }
    }

    "return error response with not found status if table not found" in {
      when(service.list(table.entity.ownerId, Seq(table.id))) thenReturn
        future(Left(InternalTableServiceError.TableNotFound(table.id)))

      Get(s"/tables-references?userId=${ table.entity.ownerId }&tableIds=${ table.id }")
        .withCredentials(validCreds)
        .check {
          status shouldBe StatusCodes.NotFound
          responseAs[ErrorResponse].code shouldBe TableReferenceResponse.Errors.Table1.code
        }
    }

    "return 200 Ok response if user have permission and all tables are found" in {
      when(service.list(table.entity.ownerId, Seq(table.id))) thenReturn
        future(Right(List(table)))

      Get(s"/tables-references?userId=${ table.entity.ownerId }&tableIds=${ table.id }")
        .withCredentials(validCreds)
        .check {
          status shouldBe StatusCodes.OK
        }
    }

  }
}

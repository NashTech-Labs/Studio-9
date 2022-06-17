package baile.services.argo

import java.util.Date
import akka.event.LoggingAdapter
import akka.http.scaladsl.HttpsConnectionContext
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse, StatusCodes }
import akka.http.scaladsl.settings.ConnectionPoolSettings
import baile.BaseSpec
import baile.services.http.exceptions.UnexpectedResponseException
import cortex.api.argo.ConfigSetting
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when

class ArgoServiceSpec extends BaseSpec {

  val argoService = new ArgoService(conf, httpMock)
  val date = new Date()
  val configSetting = ConfigSetting(
    serviceName = "serviceName",
    settingName = "settingName",
    settingValue = "settingValue",
    tags = Seq("tags"),
    createdAt = date,
    updatedAt = date
  )

  "ArgoService#setConfigValue" should {
    "return success response" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      )).thenReturn(future(HttpResponse(
        status = StatusCodes.OK,
        entity = httpEntity(configSetting)
      )))

      whenReady(argoService.setConfigValue("serviceName", "key", "value")) { response =>
        response shouldBe configSetting
      }
    }

    "return error response" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      )).thenReturn(future(HttpResponse(status = StatusCodes.BadRequest)))

      whenReady(argoService.setConfigValue("serviceName", "key", "value").failed)(_
        shouldBe a[UnexpectedResponseException])
    }
  }

  "ArgoService#getConfigValue" should {
    "return success response" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      )).thenReturn(future(HttpResponse(
        status = StatusCodes.OK,
        entity = httpEntity(configSetting)
      )))

      whenReady(argoService.getConfigValue("serviceName", "key")) { response =>
        response shouldBe configSetting
      }
    }

    "return error response" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      )).thenReturn(future(HttpResponse(status = StatusCodes.BadRequest)))

      whenReady(argoService.getConfigValue("serviceName", "key").failed)(_
        shouldBe a[UnexpectedResponseException])
    }
  }

  "ArgoService#getConfigValues" should {
    "return success response" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      )).thenReturn(future(HttpResponse(
        status = StatusCodes.OK,
        entity = httpEntity(List(configSetting))
      )))

      whenReady(argoService.getConfigValues("serviceName")) { response =>
        response shouldBe List(configSetting)
      }
    }

    "return error response" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      )).thenReturn(future(HttpResponse(status = StatusCodes.BadRequest)))

      whenReady(argoService.getConfigValues("serviceName").failed)(_
        shouldBe a[UnexpectedResponseException])
    }
  }

  "ArgoService#deleteConfigValue" should {
    "return success response" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      )).thenReturn(future(HttpResponse(
        status = StatusCodes.OK,
        entity = httpEntity(List(configSetting))
      )))

      argoService.deleteConfigValue("serviceName", "key").futureValue
    }

    "return error response" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      )).thenReturn(future(HttpResponse(status = StatusCodes.BadRequest)))

      whenReady(argoService.deleteConfigValue("serviceName", "key").failed)(_
        shouldBe a[UnexpectedResponseException])
    }
  }

}

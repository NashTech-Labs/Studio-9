package baile.services.images

import java.nio.file.Paths
import java.time.Instant
import java.util.UUID

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{ FileIO, Source }
import akka.util.ByteString
import baile.ExtendedBaseSpec
import baile.RandomGenerators._
import baile.dao.images.PictureDao
import baile.daocommons.WithId
import baile.daocommons.filters.Filter
import baile.daocommons.sorting.SortBy
import baile.domain.images.AlbumLabelMode.Classification
import baile.domain.images.AlbumStatus.Active
import baile.domain.images.augmentation.AugmentationType
import baile.domain.images.{ AlbumType, _ }
import baile.domain.usermanagement.{ RegularUser, Role, User, UserStatus }
import baile.services.common.FileUploadService
import baile.services.images.AlbumService.AlbumServiceError
import baile.services.images.PictureService.{ PictureServiceError, PictureServiceExportError }
import baile.services.remotestorage.{ File, S3StorageService, StreamedFile }
import baile.services.usermanagement.util.TestData.SampleUser
import cats.implicits._
import org.scalatest.BeforeAndAfterEach

import scala.concurrent.ExecutionContext

class PictureServiceSpec extends ExtendedBaseSpec with BeforeAndAfterEach {

  trait Setup {
    implicit val user: User = SampleUser
    val dao: PictureDao = mock[PictureDao]
    val albumService: AlbumService = mock[AlbumService]
    val imagesCommonService: ImagesCommonService = mock[ImagesCommonService]
    val remoteStorage: S3StorageService = mock[S3StorageService]
    val uploadService: FileUploadService = mock[FileUploadService]
    val service = new PictureService(imagesCommonService, albumService, remoteStorage, uploadService, dao)
    val dateAndTime: Instant = Instant.now
    val secondUser = RegularUser(
      id = UUID.fromString("e4475008-a1b0-4e22-9103-633bd1f1b437"),
      username = "userName",
      firstName = "firstName",
      lastName = "lastName",
      email = "email",
      status = UserStatus.Active,
      created = dateAndTime,
      updated = dateAndTime,
      permissions = Seq(),
      role = Role.User
    )
    val album = WithId(Album(
      ownerId = user.id,
      name = "name",
      status = Active,
      `type` = AlbumType.Source,
      labelMode = Classification,
      inLibrary = true,
      created = Instant.now(),
      updated = Instant.now(),
      picturesPrefix = "albums/name",
      description = None,
      augmentationTimeSpentSummary = None

    ), "albumId")

    val picture = WithId(Picture(
      albumId = "albumId",
      filePath = "filepath",
      fileName = "my-pic",
      fileSize = Some(0),
      caption = None,
      predictedCaption = None,
      tags = Seq.empty,
      predictedTags = Seq.empty,
      meta = Map.empty,
      originalPictureId = None,
      appliedAugmentations = None
    ), "id")
    val fileRef = new java.io.File("SomeFile")

  }

  "PictureService#createPicture" should {
    trait SetupRight extends Setup {
      imagesCommonService.getImagesPathPrefix(any[Album]) shouldReturn "path"
      albumService.get(*, *)(any[User]) shouldReturn future(album.asRight)
      albumService.ensureCanUpdateContent(*, *) shouldReturn future(().asRight)
      remoteStorage.copyFrom(*, *, *)(any[ExecutionContext], any[Materializer]) shouldReturn future {
        File("SomeFile", 256L, Instant.now())
      }
      remoteStorage.path(*, *)  shouldReturn "full/path"

      uploadService.fileStorage shouldReturn remoteStorage
      remoteStorage.streamFile(*) shouldAnswer {
        (file: String) => future(StreamedFile(
          File(s"/images/${ file }", 100l, Instant.now),
          FileIO.fromPath(Paths.get(getClass.getResource(s"/images/${ file }").getPath))
            .mapMaterializedValue(_ => NotUsed)
        ))
      }
      dao.create(any[String => Picture])(any[ExecutionContext]) shouldAnswer {
        (handler: String => Picture) => future(WithId(handler("id"), "id"))
      }
      uploadService.deleteUploadedFile(*) shouldReturn future(())
    }

    "create Picture for PNG file" in new SetupRight {
      whenReady(service.create(
        "albumId", "my-pic", "png.png", "file-name"
      )) { result =>
        assert(result.isRight)
      }
    }

    "create Picture for indexed PNG file" in new SetupRight {
      whenReady(service.create(
        "albumId", "my-pic", "png2.png", "file-name"
      )) { result =>
        assert(result.isRight)
      }
    }

    "create Picture for GIF file" in new SetupRight {
      whenReady(service.create(
        "albumId", "my-pic", "gif.gif", "file-name"
      )) { result =>
        assert(result.isRight)
      }
    }

    "create Picture for JPG file" in new SetupRight {
      whenReady(service.create(
        "albumId", "my-pic", "jpeg.jpg", "file-name"
      )) { result =>
        assert(result.isRight)
      }
    }

    "return access denied when owner missmatch" in new Setup {
      albumService.get("albumId", *)(secondUser) shouldReturn future(AlbumServiceError.AccessDenied.asLeft)
      whenReady(service.create(
        "albumId", "my-pic", "file-path", "file-name"
      )(secondUser, implicitly[Materializer])) {
        _ shouldBe Left(PictureServiceError.AccessDenied)
      }
    }

    "fail when album is in use " in new Setup {
      albumService.get(*, *)(any[User]) shouldReturn future(album.asRight)
      albumService.ensureCanUpdateContent(*, *) shouldReturn future(Left(AlbumServiceError.AlbumInUse))
      whenReady(service.create(
        "albumId", "my-pic", "file-path", "file-name"
      )) {
        _ shouldBe a [Left[PictureServiceError.PictureOperationUnavailable, _]]
      }
    }

    "fail if file is not a picture" in new Setup {
      albumService.get(*, *)(any[User]) shouldReturn future(album.asRight)
      albumService.ensureCanUpdateContent(*, *) shouldReturn future(().asRight)

      uploadService.fileStorage shouldReturn remoteStorage
      remoteStorage.streamFile("test.csv") shouldReturn future(StreamedFile(
        File("/test.csv", 100l, Instant.now),
        FileIO.fromPath(Paths.get(getClass.getResource("/test.csv").getPath))
          .mapMaterializedValue(_ => NotUsed)
      ))

      whenReady(service.create(
        "albumId", "my-pic", "test.csv", "file-name"
      )) {
        _ shouldBe Left(PictureServiceError.PictureTypeUnknown)
      }
    }

  }

  "PictureService#updatePicture" should {

    "update Picture" in new Setup {
      future(Some(picture)) willBe returned by dao.get(*)(*)
      albumService.get(*, *)(*) shouldReturn future(album.asRight)
      albumService.ensureCanUpdateContent(*, *) shouldReturn future(().asRight)
      future(Some(picture)) willBe returned by dao.get(eqTo(picture.id))(*)
      dao.update(picture.id, *)(*) shouldReturn future(Some(picture))
      whenReady(service.update(
        "albumId", "id", Some("test"), Seq.empty[PictureTag]
      )) { result =>
        assert(result.isRight)
      }
    }

    "return access denied when owner missmatch" in new Setup {
      albumService.get("albumId", *)(secondUser) shouldReturn future(AlbumServiceError.AccessDenied.asLeft)
      whenReady(service.update(
        "albumId", "id", Some("test"), Seq.empty[PictureTag]
      )(secondUser)) {
        _ shouldBe Left(PictureServiceError.AccessDenied)
      }
    }

    "return picture not found when picture does not exists with given id" in new Setup {
      albumService.ensureCanUpdateContent(*, *) shouldReturn future(().asRight)
      future(None) willBe returned by dao.get(*)(any[ExecutionContext])
      albumService.get(*, *)(*) shouldReturn future(album.asRight)
      whenReady(service.update(
        "albumId", "i2d", Some("test"), Seq.empty[PictureTag]
      )) {
        _ shouldBe Left(PictureServiceError.PictureNotFound)
      }
    }

    "return album in use error if album cannot be updated" in new Setup {
      albumService.get(*, *)(*) shouldReturn future(album.asRight)
      albumService.ensureCanUpdateContent(*, *) shouldReturn
        future(AlbumServiceError.AlbumInUse.asLeft)
      whenReady(service.update(
        "albumId", "id", Some("test"), Seq.empty[PictureTag]
      )) {
        _ shouldBe a [Left[PictureServiceError.PictureOperationUnavailable, _]]
      }
    }
  }

  "PictureServiceSpec#Delete Picture" should {

    "delete pictures" in new Setup {
      future(Some(picture)) willBe returned by dao.get(*)(*)
      albumService.get(*, *)(any[User]) shouldReturn future(album.asRight)
      albumService.ensureCanUpdateContent(*, *) shouldReturn future(().asRight)
      future(Some(picture)) willBe returned by dao.get(eqTo(picture.id))(any[ExecutionContext])
      dao.delete(*)(any[ExecutionContext]) shouldReturn future(true)
      whenReady(service.delete("albumId", "id")) { result =>
        assert(result.isRight)
      }
    }

    "return access denied when owner missmatch" in new Setup {
      albumService.get(eqTo("albumId"), *)(*) shouldReturn
        future(AlbumServiceError.AccessDenied.asLeft)
      whenReady(service.delete("albumId", "id")(user)) {
        _ shouldBe Left(PictureServiceError.AccessDenied)
      }
    }

    "return picture not found when picture does not exists with given id" in new Setup {
      albumService.get(*, *)(any[User]) shouldReturn future(album.asRight)
      albumService.ensureCanUpdateContent(*, *) shouldReturn future(().asRight)
      future(None) willBe returned by dao.get(*)(any[ExecutionContext])
      whenReady(service.delete("albumId", "picId")) {
        _ shouldBe Left(PictureServiceError.PictureNotFound)
      }
    }

    "fail when album is in use " in new Setup {
      albumService.get(*, *)(any[User]) shouldReturn future(album.asRight)
      albumService.ensureCanUpdateContent(any[WithId[Album]], any[User]) shouldReturn
        future(AlbumServiceError.AlbumInUse.asLeft)
      whenReady(service.delete("albumId", "picId")) {
        _ shouldBe a [Left[PictureServiceError.PictureOperationUnavailable, _]]
      }
    }
  }

  "PictureService#list" should {
    "get pictures when labels and search are given" in new Setup {
      albumService.get(*, *)(any[User]) shouldReturn future(album.asRight)
      dao.list(
        any[Filter],
        any[Int],
        any[Int],
        any[Option[SortBy]]
      )(any[ExecutionContext]) shouldReturn future(Seq(picture))
      dao.count(
        any[Filter]
      )(any[ExecutionContext]) shouldReturn future(1)
      whenReady(service.list("id", Some(Seq("value")), Some(""), Nil, 1, 10)) {
        _ shouldBe Right((Seq(picture), 1))
      }
    }

    "get pictures on the basis of AlbumId when neither labels nor search are given" in new Setup {
      albumService.get(*, *)(any[User]) shouldReturn future(album.asRight)
      dao.list(
        any[Filter],
        any[Int],
        any[Int],
        any[Option[SortBy]]
      )(any[ExecutionContext]) shouldReturn future(Seq(picture))
      dao.count(
        any[Filter]
      )(any[ExecutionContext]) shouldReturn future(1)
      whenReady(service.list("id", None, None, Nil, 1, 10)) {
        _ shouldBe Right((Seq(picture), 1))
      }
    }

    "return access denied when owner missmatch" in new Setup {
      albumService.get(eqTo("id"), *)(*) shouldReturn
        future(AlbumServiceError.AccessDenied.asLeft)
      whenReady(service.list("id", None, None, Nil, 1, 10)(user)) {
        _ shouldBe Left(PictureServiceError.AccessDenied)
      }
    }

    "get pictures when labels, search and augmentationType are given" in new Setup {
      future(album.asRight) willBe returned by albumService.get(*, *)(*)
      future(Seq(picture)) willBe returned by dao.list(*, *, *, *)(*)
      future(1) willBe returned by dao.count(*)(*)
      whenReady(service.list(
        "id",
        Some(Seq("value")),
        Some(""), Nil,
        1,
        10,
        None,
        Some(Seq(Some(AugmentationType.Blurring)))
      )) {
        _ shouldBe Right((Seq(picture), 1))
      }
    }
  }

  "PictureService#getPicturesWithUpdatedExternalURL" should {
    "generate url" in new Setup {
      remoteStorage.path(*, *) shouldReturn "path"
      imagesCommonService.getImagesPathPrefix(*) shouldReturn "full/path"
      remoteStorage.getExternalUrl(*, *) shouldReturn "filepath"
      albumService.get(*, *)(*) shouldReturn future(album.asRight)
      whenReady(service.signPictures("albumId", Seq(picture))) {
        _ shouldBe Right(Seq(picture))
      }
    }
  }

  "PictureService#addPictures" should {

    "add picture with keeping existing pictures" in new Setup {
      imagesCommonService.attachPictures(*, *) shouldReturn future(())
      albumService.get(*, *)(*) shouldReturn future(album.asRight)
      albumService.ensureCanUpdateContent(*, *) shouldReturn future(().asRight)
      whenReady(service.addPictures(album.id, Seq(picture.entity), keepExisting = true)) {
        _ shouldBe ().asRight
      }
    }

    "add picture without keeping existing pictures" in new Setup {
      imagesCommonService.attachPictures(*, *) shouldReturn future(())
      albumService.get(*, *)(*) shouldReturn future(album.asRight)
      albumService.ensureCanUpdateContent(*, *) shouldReturn future(().asRight)
      dao.deleteMany(*)(*) shouldReturn future(randomInt(1))
      whenReady(service.addPictures(album.id, Seq(picture.entity), keepExisting = false)) {
        _ shouldBe ().asRight
      }
    }

    "fail if list of picture meta data is empty" in new Setup {
      whenReady(service.addPictures(album.id, Seq(), keepExisting = false)) {
        _ shouldBe PictureServiceError.PicturesNotFound.asLeft
      }
    }

  }

  "PictureService#getLabelsStats" should {
    "fetch stats from DAO when album type is Source" in new Setup {
      albumService.get(*,*)(*) shouldReturn
        future(AlbumServiceError.AlbumNotFound.asLeft)
      albumService.get(eqTo(album.id), *)(any[User]) shouldReturn future(album.asRight)
      dao.getLabelsStats(*)(any[ExecutionContext]) shouldReturn future(Map.empty[String, Int])
      dao.getLabelsStats(eqTo(album.id))(any[ExecutionContext]) shouldReturn future(Map("foo" -> 1))

      whenReady(service.getLabelsStats(album.id)) {
        _ shouldBe Right(Map("foo" -> 1))
      }
    }

    "fetch stats from DAO when album type is Derived" in new Setup {
      val albumWithDerivedType = WithId(album.entity.copy(`type` = AlbumType.Derived), "albumId")
      albumService.get(*, *)(any[User]) shouldReturn
        future(AlbumServiceError.AlbumNotFound.asLeft)

      albumService.get(eqTo(album.id), *)(any[User]) shouldReturn future(albumWithDerivedType.asRight)

      dao.getAllLabelsStats(*)(any[ExecutionContext]) shouldReturn future(Map.empty[String, Int])

      dao.getAllLabelsStats(eqTo(album.id))(any[ExecutionContext]) shouldReturn future(Map("foo" -> 1))

      whenReady(service.getLabelsStats(album.id)) {
        _ shouldBe Right(Map("foo" -> 1))
      }
    }

    "fetch stats from DAO when album type is TrainResults" in new Setup {
      val albumWithTrainResultsType = WithId(album.entity.copy(`type` = AlbumType.TrainResults), "albumId")
      albumService.get(*, *)(any[User]) shouldReturn
        future(AlbumServiceError.AlbumNotFound.asLeft)

      albumService.get(eqTo(album.id), *)(any[User]) shouldReturn future(albumWithTrainResultsType.asRight)

      dao.getPredictedLabelsStats(*)(any[ExecutionContext]) shouldReturn future(Map.empty[String, Int])
      dao.getPredictedLabelsStats(eqTo(album.id))(any[ExecutionContext]) shouldReturn future(Map("foo" -> 1))

      whenReady(service.getLabelsStats(album.id)) {
        _ shouldBe Right(Map("foo" -> 1))
      }
    }

    "fail on unknown album" in new Setup {
      albumService.get(*, *)(any[User]) shouldReturn
        future(AlbumServiceError.AlbumNotFound.asLeft)
      whenReady(service.getLabelsStats(randomString())) {
        _ shouldBe Left(PictureServiceError.AlbumNotFound)
      }
    }

    "fail on forbidden album" in new Setup {
      albumService.get(*, *)(any[User]) shouldReturn
        future(AlbumServiceError.AccessDenied.asLeft)
      whenReady(service.getLabelsStats("forbiddenAlbumId")) {
        _ shouldBe Left(PictureServiceError.AccessDenied)
      }
    }
  }

  "PictureService#exportLabels" should {
    "fetch data from DAO" in new Setup {
      albumService.get(*)(any[User]) shouldReturn future(AlbumServiceError.AlbumNotFound.asLeft)
      albumService.get(eqTo(album.id))(any[User]) shouldReturn future(album.asRight)
      dao.exportTags(*)(any[ExecutionContext]) shouldReturn Source.empty
      dao.exportTags(eqTo(album.id))(any[ExecutionContext]) shouldReturn
        Source(List(
          PictureTagsSummary(
            randomString(),
            Seq(PictureTag(label = randomString())),
            Seq.empty
          )
        ))
      whenReady(service.exportLabels(album.id)) { result =>
        whenReady(result.right.get.runFold(Seq.empty[ByteString])(_ :+ _))(_.size should be > 0)
      }
    }

    "fail on unknown album" in new Setup {
      albumService.get(*)(any[User]) shouldReturn future(AlbumServiceError.AlbumNotFound.asLeft)
      whenReady(service.exportLabels(randomString())) {
        _ shouldBe Left(PictureServiceExportError.AlbumNotFound)
      }
    }

    "fail on forbidden album" in new Setup {
      albumService.get(*)(any[User]) shouldReturn future(AlbumServiceError.AlbumNotFound.asLeft)
      albumService.get(eqTo("forbiddenAlbumId"))(any[User]) shouldReturn future(AlbumServiceError.AccessDenied.asLeft)
      whenReady(service.exportLabels("forbiddenAlbumId")) {
        _ shouldBe Left(PictureServiceExportError.AccessDenied)
      }
    }

    "use labels for Source album" in new Setup {
      albumService.get(*)(any[User]) shouldReturn future(AlbumServiceError.AlbumNotFound.asLeft)
      albumService.get(eqTo(album.id))(any[User]) shouldReturn
        future(WithId(album.entity.copy(`type` = AlbumType.Source), album.id).asRight)
      dao.exportTags(*)(any[ExecutionContext]) shouldReturn Source.empty
      dao.exportTags(eqTo(album.id))(any[ExecutionContext]) shouldReturn Source(List(
        PictureTagsSummary(
          randomString(),
          Seq(PictureTag(label = "needle")),
          Seq.empty
        )
      ))

      whenReady(service.exportLabels(album.id)) { result =>
        whenReady(result.right.get.runFold(Seq.empty[ByteString])(_ :+ _)) { result =>
          result.size should be > 0
          result.map(_.utf8String).mkString should include("needle")
        }
      }
    }

    "use predicted labels for Derived/Train results album" in new Setup {
      albumService.get(*)(any[User]) shouldReturn future(AlbumServiceError.AlbumNotFound.asLeft)
      albumService.get(eqTo(album.id))(any[User]) shouldReturn
        future(WithId(
          album.entity.copy(`type` = randomOf(AlbumType.Derived, AlbumType.TrainResults)),
          album.id
        ).asRight)
      dao.exportTags(*)(any[ExecutionContext]) shouldReturn Source.empty
      dao.exportTags(eqTo(album.id))(any[ExecutionContext]) shouldReturn
        Source(List(
          PictureTagsSummary(
            randomString(),
            Seq.empty,
            Seq(PictureTag(label = "needle"))
          )
        ))

      whenReady(service.exportLabels(album.id)) { result =>
        whenReady(result.right.get.runFold(Seq.empty[ByteString])(_ :+ _)) { result =>
          result.size should be > 0
          result.map(_.utf8String).mkString should include("needle")
        }
      }
    }

    "use predicted tags for localization album" in new Setup {
      albumService.get(*)(any[User]) shouldReturn future(AlbumServiceError.AlbumNotFound.asLeft)
      albumService.get(eqTo(album.id))(any[User]) shouldReturn future(WithId(
        album.entity.copy(labelMode = AlbumLabelMode.Localization),
        album.id
      ).asRight)
      dao.exportTags(*)(any[ExecutionContext]) shouldReturn Source.empty
      dao.exportTags(eqTo(album.id))(any[ExecutionContext]) shouldReturn Source(List(
        PictureTagsSummary(
          randomString(),
          Seq(PictureTag(
            label = "needle",
            area = Some(PictureTagArea(
              left = 10,
              top = 20,
              width = 100,
              height = 200
            ))
          )),
          Seq.empty
        )
      ))

      whenReady(service.exportLabels(album.id)) { result =>
        whenReady(result.right.get.runFold(Seq.empty[ByteString])(_ :+ _)) { result =>
          result.size should be > 0
          result.map(_.utf8String).mkString should include("needle,10,20,110,220")
        }
      }
    }

  }

}

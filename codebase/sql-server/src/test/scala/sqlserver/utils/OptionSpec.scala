package sqlserver.utils


import cats.data.EitherT
import sqlserver.BaseSpec
import sqlserver.utils.validation.Option.OptionOps
import cats.implicits._

import scala.concurrent.Future

class OptionSpec extends BaseSpec {

  "Option#validate[Either]" should {
    "validate option value" in {
      Option(3).validate((x: Int) => if (x < 4) Left(()) else Right(())) shouldBe Left(())
    }

    "not do anything if option is empty" in {
      Option.empty[Int].validate((x: Int) => x.asLeft) shouldBe Right(())
    }
  }

  "Option#validate[Future[Either]]" should {
    "validate option value" in {
      whenReady(
        Option(3).validate((x: Int) => if (x < 4) future(Left(())) else future(Right(())))
      )(_ shouldBe Left(()))
    }

    "not do anything if option is empty" in {
      whenReady(
        Option.empty[Int].validate((x: Int) => future(x.asLeft))
      )(_ shouldBe Right(()))
    }
  }

  "Option#validate[EitherT[Future]]" should {
    "validate option value" in {
      whenReady(Option(3).validate(
        (x: Int) => if (x < 4) EitherT.leftT[Future, Unit](()) else EitherT.rightT[Future, Unit](())
      ).value)(_ shouldBe Left(()))
    }

    "not do anything if option is empty" in {
      whenReady(
        Option.empty[Int].validate((x: Int) => EitherT.leftT[Future, Unit](())).value
      )(_ shouldBe Right(()))
    }
  }
}

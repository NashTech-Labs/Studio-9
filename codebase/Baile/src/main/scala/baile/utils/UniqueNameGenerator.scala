package baile.utils

import cats.{ Eval, Monad }
import cats.implicits._

import scala.language.higherKinds

object UniqueNameGenerator {

  def generateUniqueName[F[_]: Monad](
    prefix: String,
    suffixDelimiter: String = "_"
  )(uniquenessValidator: String => F[Boolean]): F[String] = {
    val candidates = prefix +: Stream.from(1).map(prefix + suffixDelimiter + _)
    candidates.foldr(Eval.now(Monad[F].pure(none[String]))) { (candidate, soFar) =>
      Eval.now {
        uniquenessValidator(candidate).flatMap { isUnique =>
          if (isUnique) Monad[F].pure(candidate.some) else soFar.value
        }
      }
    }
  }.value.map(_.get)

}

package baile.utils

object CollectionExtensions {

  def seqToOptionalNonEmptySeq[T](s: Seq[T]): Option[Seq[T]] =
    if (s.nonEmpty) Some(s) else None

  def mapToOptionalNonEmptyMap[K, T](s: Map[K, T]): Option[Map[K, T]] =
    if (s.nonEmpty) Some(s) else None

}

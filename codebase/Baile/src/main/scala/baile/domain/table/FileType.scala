package baile.domain.table

sealed trait FileType

object FileType {

  case object JSON extends FileType

  case object CSV extends FileType

}

package sqlserver.domain.table

import akka.NotUsed
import akka.stream.scaladsl.Source

case class TableQueryResult(
  source: Source[TableRow, NotUsed],
  columnsInfo: Seq[Column],
  rowCount: Option[Long]
)

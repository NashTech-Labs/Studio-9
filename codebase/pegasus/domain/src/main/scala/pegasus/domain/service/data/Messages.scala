package pegasus.domain.service.data

import pegasus.domain.service.ServiceMessage

case class LoadData(tableName: String, dataSource: String, delimiter: String) extends ServiceMessage
case class UnloadData(tableName: String, outputObjectPath: String, delimiter: String) extends ServiceMessage

package pegasus.domain.rest.data

import pegasus.domain.rest.HttpContract

case class LoadDataContract(tableName: String, dataSource: String, delimiter: String) extends HttpContract
case class UnloadDataContract(tableName: String, outputObjectPath: String, delimiter: String) extends HttpContract

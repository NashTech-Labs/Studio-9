package baile.daocommons

case class WithId[+T](entity: T, id: String)

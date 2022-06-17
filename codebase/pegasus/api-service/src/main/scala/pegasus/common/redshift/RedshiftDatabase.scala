package pegasus.common.redshift

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import slick.jdbc.JdbcBackend.Database

trait RedshiftDatabase extends Extension {
  val database: Database
}

object RedshiftDatabase extends ExtensionId[RedshiftDatabase] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): RedshiftDatabase = new RedshiftDatabase {
    override val database = Database.forConfig("slick.redshift")
  }

  override def lookup(): ExtensionId[_ <: Extension] = RedshiftDatabase
}
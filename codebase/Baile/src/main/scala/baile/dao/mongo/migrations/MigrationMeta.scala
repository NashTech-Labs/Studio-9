package baile.dao.mongo.migrations

import java.time.Instant

case class MigrationMeta(id: String, applied: Instant)

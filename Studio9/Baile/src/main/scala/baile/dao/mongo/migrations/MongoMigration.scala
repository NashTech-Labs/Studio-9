package baile.dao.mongo.migrations

import java.time.LocalDate

import com.mongodb.client.model.{ CollationStrength, CreateCollectionOptions }
import org.mongodb.scala.model.Collation
import org.mongodb.scala.{ Completed, MongoDatabase }

import scala.concurrent.{ ExecutionContext, Future }

private[migrations] abstract class MongoMigration(name: String, date: LocalDate) {

  val id: String = name + "_" + date.toString

  private[migrations] def execute(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit]

}

object MongoMigration {

  def createCollection(name: String)(implicit db: MongoDatabase): Future[Completed] = {
    val options = new CreateCollectionOptions()
      .collation(Collation.builder()
        .locale("en_US")
        .collationStrength(CollationStrength.SECONDARY)
        .build()
      )
    db.createCollection(name, options).toFuture()
  }

}

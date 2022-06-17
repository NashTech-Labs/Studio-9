package baile.dao.mongo.migrations

import java.time.Instant

import baile.daocommons.filters.TrueFilter
import org.mongodb.scala.MongoDatabase

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

class MigrationsExecutor(migrationMetaDao: MigrationMetaDao) {

  final def migrate(
    migrations: List[MongoMigration]
  )(implicit db: MongoDatabase, ec: ExecutionContext): Future[Unit] = {

    def migrateNewOnes(appliedMigrations: List[MigrationMeta]): Future[Unit] =
      migrations.foldLeft(Future.successful(())) { (soFar, migration) =>
        for {
          _ <- soFar
          _ <- appliedMigrations.find(_.id == migration.id) match {
            case Some(existingMigration) => Future.successful(println(
              s"Skipping migration '${ migration.id }' as it was already applied on ${ existingMigration.applied }"
            ))
            case None =>
              val result =
                for {
                  _ <- migration.execute
                  _ <- migrationMetaDao.create(MigrationMeta(migration.id, Instant.now))
                } yield ()
              result.andThen {
                case Success(_) =>
                  println(s"Applied migration '${ migration.id }'")
                case Failure(ex) =>
                  println(s"Failed to apply migration '${ migration.id }' because of error: '$ex'")
            }
          }
        } yield ()
      }

    for {
      appliedMigrations <- migrationMetaDao.listAll(TrueFilter)
      _ <- migrateNewOnes(appliedMigrations.map(_.entity).toList)
    } yield ()
  }

}

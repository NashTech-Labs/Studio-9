package orion.testkit.service

import java.io.File

import akka.testkit.TestKit
import org.apache.commons.io.FileUtils
import org.scalatest.BeforeAndAfterAll

import scala.util._

trait PersistenceSupport extends BeforeAndAfterAll { self: ServiceBaseSpec =>

  override def beforeAll(): Unit = {
    deleteStorageLocations()
  }

  override def afterAll(): Unit = {
    deleteStorageLocations()
    TestKit.shutdownActorSystem(system)
  }

  def deleteStorageLocations(): Unit = {
    val storageLocations = Seq(
      "akka.persistence.journal.leveldb.dir",
      "akka.persistence.journal.leveldb-shared.store.dir",
      "akka.persistence.snapshot-store.local.dir"
    ).map { s => new File(system.settings.config.getString(s)) }

    storageLocations.foreach(dir => Try(FileUtils.deleteDirectory(dir)))
  }
}


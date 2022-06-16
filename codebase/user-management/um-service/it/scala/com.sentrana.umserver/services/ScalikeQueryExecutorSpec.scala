package com.sentrana.umserver.services

import com.sentrana.umserver.WithSQLSupport
import org.scalatestplus.play.PlaySpec
import play.api.Configuration
import play.api.test.FakeApplication

import scala.util.{Failure, Success}

/**
  * Created by Alexander on 25.06.2016.
  */
class ScalikeQueryExecutorSpec extends PlaySpec with WithSQLSupport {
  private val config = FakeApplication().injector.instanceOf(classOf[Configuration])
  protected val connectionProvider = new ScalikeQueryExecutor(config)

  private val testConnectionPool = "testConnectionPool"
  private val url1 = "jdbc:h2:mem:play1"

  private implicit val k = DataExtractor.extractData(_)

  "ConnectionProviderService" must {
    "check whether connection pool is present" in {
      connectionProvider.hasConnectionPool(testConnectionPool) mustBe false
    }

    "add new connection pools" in {
      connectionProvider.addConnectionPool(testConnectionPool, driver, url1, username, password)
      connectionProvider.hasConnectionPool(testConnectionPool) mustBe true
    }

    "get connection pool" in {
      connectionProvider.getConnectionPool(testConnectionPool) match {
        case Success(a) => ()
        case _ => fail(s"Connection pool: ${testConnectionPool} should be present")
      }
    }

    "not get connection pool" in {
      intercept[IllegalStateException] {
        connectionProvider.getConnectionPool(testConnectionPool + "_test").get
      }
      ()
    }

    "create data_by_column_index and data_by_column_names tables" in {
      executeQuery(testConnectionPool, createTableQuery("data_by_column_names", "value", "display_text"))
      executeQuery(testConnectionPool, createTableQuery("data_by_column_index", "id", "name"))
    }

    "create test data for data_by_column_index" in {
      executeQuery(testConnectionPool, insertQuery("data_by_column_names", "value", "display_text", "test_value", "test_display_text"))
    }

    "create test data for data_by_column_names" in {
      executeQuery(testConnectionPool, insertQuery("data_by_column_index", "id", "name", "1", "test_name"))
    }

    "not allow to drop table" in {
      connectionProvider.executeReadOnlyQuery[String, String](testConnectionPool)("drop table data_by_column_names").isFailure mustBe true
    }

    "not allow to insert data" in {
      connectionProvider.executeReadOnlyQuery[String, String](testConnectionPool)("insert into data_by_column_index (id) values(1)").isFailure mustBe true
    }

    "return `value` and `display text`" in {
      connectionProvider.executeReadOnlyQuery[String, String](testConnectionPool)(getValidValuesQuery("data_by_column_names")) match {
        case Success(data) => data.size mustBe 1
          data.head mustBe ("test_value" -> "test_display_text")
        case Failure(e) => fail(e)
      }
    }

    "return 1st and 2nd columns values if no `value` and `display_text` columns are present" in {
      connectionProvider.executeReadOnlyQuery[String, String](testConnectionPool)("select * from data_by_column_index") match {
        case Success(data) => data.size mustBe 1
          data.head mustBe ("1" -> "test_name")
        case Failure(e) => fail(e)
      }
    }
  }
}

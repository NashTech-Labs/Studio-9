package baile.dao.mongo

import baile.BaseSpec
import org.mongodb.scala.Document
import baile.dao.mongo.BsonHelpers._
import org.mongodb.scala.bson.{ BsonArray, BsonDocument, BsonInt32, BsonString }

class BsonHelpersSpec extends BaseSpec {
  val doc: Document = Document(
    """{
      | "int": 1,
      | "float": 1.5,
      | "string": "foo",
      | "child": {
      |   "val": "bar"
      | },
      | "list": ["a", "b", "c"]
    }""".stripMargin
  )

  "DocumentExtensions.getMandatory" should {
    "get existing item" in {
      val item = doc.getMandatory[BsonInt32]("int")

      item.getValue shouldBe 1
    }

    "get child document item" in {
      val item = doc.getMandatory[BsonDocument]("child")

      Option(item) should not be empty
    }

    "throw if no key exists" in {
      intercept[MongoMissingKeyException](doc.getMandatory[BsonInt32]("noInt"))
    }

    "throw if no value format is incorrect" in {
      intercept[MongoInvalidValueException](doc.getMandatory[BsonString]("int"))
    }
  }

  "DocumentExtensions.getChild" should {
    "get Document instance" in {
      val child = doc.getChild("child")

      child should not be empty
    }

    "get None for missing key" in {
      val child = doc.getChild("noChild")

      child shouldBe empty
    }
  }

  "DocumentExtensions.getChildMandatory" should {
    "get Document instance" in {
      val child = doc.getChildMandatory("child")

      Option(child) should not be empty
    }

    "throw if no key exists" in {
      intercept[MongoMissingKeyException](doc.getChildMandatory("noChild"))
    }

    "throw if no value format is incorrect" in {
      intercept[MongoInvalidValueException](doc.getChildMandatory("int"))
    }
  }

  "BsonArrayExtensions.asScala" should {
    val array = BsonArray(Seq(BsonString("1")))

    "convert to a scala sequence" in {
      val item = array.asScala.head

      item shouldBe BsonString("1")
    }
  }

  "BsonArrayExtensions.map" should {
    val array = BsonArray(Seq(BsonString("1")))

    "map thru values" in {
      array.map { item =>
        item shouldBe BsonString("1")
      }
    }
  }
}

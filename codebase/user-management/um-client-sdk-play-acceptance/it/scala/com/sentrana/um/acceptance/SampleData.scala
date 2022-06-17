package com.sentrana.um.acceptance

import java.time.ZonedDateTime
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

import com.mongodb.ConnectionString
import com.sentrana.umserver.shared.dtos.{DataFilterInstance, Permission}
import com.sentrana.umserver.shared.dtos.enums.{DBType, FilterOperator, WellKnownPermissions}
import org.mongodb.scala.MongoClient
import org.mongodb.scala.bson.collection.immutable.Document
import play.api.Configuration
import play.api.libs.json.Json
import play.api.test.Helpers._

/**
  * Created by Paul Lysak on 25.04.16.
  */
object SampleData {

  import com.sentrana.um.client.play.JsonFormats.enumWrites

  private lazy val mongodbUri = configuration.getString("mongodb.um.uri").getOrElse("mongodb://localhost")
  private lazy val mongoClient = MongoClient(mongodbUri)
  private lazy val dbName = configuration.getString("mongodb.um.db").orElse({
    Option((new ConnectionString(mongodbUri)).getDatabase)
  }).getOrElse("um-server")
  private lazy val db = mongoClient.getDatabase(dbName)

  private lazy val orgsCol = db.getCollection("organizations")
  private lazy val groupsCol = db.getCollection("userGroups")
  private lazy val usersCol = db.getCollection("users")
  private lazy val applicationsCol = db.getCollection("applications")
  private lazy val dataFiltersCol = db.getCollection("dataFilters")

  implicit val dataFilterInstanceWrites = Json.writes[DataFilterInstance]
  implicit val permissionWrites = Json.writes[Permission]
  implicit val orgWrites = Json.writes[SampleOrg]
  implicit val sampleGroupWrites = Json.writes[SampleGroup]
  implicit val sampleUserWrites = Json.writes[SampleUser]
  implicit val applicationInfoWrites = Json.writes[ApplicationInfoSample]
  implicit val dataFilterInfoSettingsWrites = Json.writes[DataFilterInfoSettingsSample]
  implicit val dataFilterInfoWrites = Json.writes[DataFilterInfoSample]

  private var configuration: Configuration = _

  def init(cfg: Configuration): Unit = {
    if(configuration == null) {
      configuration = cfg
      createEntities()
    }
  }

  private def createEntities(): Unit = {
    await(orgsCol.insertMany(orgs.all.map({o => Document(Json.stringify(Json.toJson(o)))})).toFuture())
    await(groupsCol.insertMany(groups.all.map({o => Document(Json.stringify(Json.toJson(o)))})).toFuture())
    await(usersCol.insertMany(users.all.map({o => Document(Json.stringify(Json.toJson(o.encodePwd)))})).toFuture())
    await(applicationsCol.insertMany(apps.all.map({ o => Document(Json.stringify(Json.toJson(o)))})).toFuture())
    await(dataFiltersCol.insertMany(dataFilters.all.map({ o => Document(Json.stringify(Json.toJson(o)))})).toFuture())
    ()
  }

  def generateId: String = UUID.randomUUID().toString

  val DEFAULT_PASSWORD = "samplePwd"

  object dataFilters {
    val sampleDataFilterInfo1 = DataFilterInfoSample(fieldDesc = "sampleFieldDesc1", fieldName = "sampleFieldName1")
    val sampleDataFilterInfo2 = DataFilterInfoSample(fieldDesc = "sampleFieldDesc2", fieldName = "sampleFieldName2")
    val sampleDataFilterInfo3 = DataFilterInfoSample(fieldDesc = "sampleFieldDesc3", fieldName = "sampleFieldName3")

    val all = Seq(sampleDataFilterInfo1, sampleDataFilterInfo2, sampleDataFilterInfo3)
  }

  object orgs {
    val root = SampleOrg(name = "Root org")
    val org1 = SampleOrg(name = "Org 1", parentOrganizationId = Option(root.id))
    val org2 = SampleOrg(name = "Org 2", parentOrganizationId = Option(root.id))
    val orgForSearch = SampleOrg(name = "Org for search", parentOrganizationId = Option(root.id))
    val orgSelfServiceSignUp = SampleOrg(name = "Org Self Sign Up", parentOrganizationId = Option(root.id), signUpEnabled = true)

    val all = Seq(root, org1, org2, orgForSearch, orgSelfServiceSignUp)
  }

  object dataFilterInstances {
    val dataFilterInstance1 = DataFilterInstance(dataFilters.sampleDataFilterInfo1.id, FilterOperator.EQ, Set("DataFilterInstanceValue1"))
    val dataFilterInstance2 = DataFilterInstance(dataFilters.sampleDataFilterInfo2.id, FilterOperator.IN, Set("DataFilterInstanceValue2"))
    val dataFilterInstance3 = DataFilterInstance(dataFilters.sampleDataFilterInfo3.id, FilterOperator.NOT_IN, Set("DataFilterInstanceValue3"))

    val all = Set(dataFilterInstance1, dataFilterInstance2, dataFilterInstance3)
  }

  object groups {
    val superUsers = SampleGroup(name = "superUsers", organizationId = orgs.root.id, grantsPermissions = Set(Permission(WellKnownPermissions.SUPERUSER.toString)), forChildOrgs = true)
    val sampleGroup = SampleGroup(name = "sampleGroup", organizationId = orgs.root.id, grantsPermissions = Set(Permission("SAMPLE_PERMISSION")))
    val sampleSubgroup = SampleGroup(name = "sampleGroup", organizationId = orgs.root.id, parentGroupId = Option(sampleGroup.id))
    val anotherGroup = SampleGroup(name = "anotherGroup", organizationId = orgs.root.id, grantsPermissions = Set(Permission("ANOTHER_PERMISSION")))

    val all = Seq(superUsers, sampleGroup, sampleSubgroup, anotherGroup)
  }

  object users {
    val admin1 = SampleUser(username = "admin1", groupIds = Set(groups.superUsers.id), organizationId = orgs.root.id)
    val noGroupUser1 = SampleUser(username = "noGroupUser1", organizationId = orgs.root.id)
    val noGroupUser2 = SampleUser(username = "noGroupUser2", firstName = "Bill", organizationId = orgs.root.id)
    val sampleGroupUser1 = SampleUser(username = "sampleGroupUser1", groupIds = Set(groups.sampleGroup.id), organizationId = orgs.root.id)
    val sampleSubgroupUser1 = SampleUser(username = "sampleSubgroupUser1", groupIds = Set(groups.sampleSubgroup.id), organizationId = orgs.root.id)
    val anotherGroupUser1 = SampleUser(username = "anotherGroupUser1", groupIds = Set(groups.anotherGroup.id), organizationId = orgs.root.id)

    val dataFiltersUser = SampleUser(username = "dataFiltersUser", groupIds = Set(groups.superUsers.id), organizationId = orgs.root.id, dataFilterInstances = dataFilterInstances.all)
    val org1User1 = SampleUser(username = "org1User1", organizationId = orgs.org1.id)
    val org1Admin1 = SampleUser(username = "org1Admin1", groupIds = Set(groups.superUsers.id), organizationId = orgs.org1.id)
    val org2User1 = SampleUser(username = "org2User1", organizationId = orgs.org2.id)
    val org2Admin1 = SampleUser(username = "org2Admin1", groupIds = Set(groups.superUsers.id), organizationId = orgs.org2.id)

    val emailQweUser = SampleUser(username = "emailQwe", email = "qwe@somewhere.com", organizationId = orgs.root.id)
    val emailQwertyUser = SampleUser(username = "emailQwerty", email = "qwerty@somewhere.com", organizationId = orgs.root.id)
    val emailAsdUser = SampleUser(username = "emailAsd", email = "asd@somewhere.com", organizationId = orgs.root.id)

    val userForOrgSearch = SampleUser(username = "userForOrgSearch", email = "userForOrgSearch@somewhere.com", organizationId = orgs.orgForSearch.id)

    val pwdResetUser = SampleUser(username = "pwdResetUser", email = "pwdReset@somewhere.com", organizationId = orgs.root.id)

    val pwdUpdateUser = SampleUser(username = "pwdUpdateUser", email = "pwdUpdate@somewhere.com", organizationId = orgs.root.id, password = "oldPassword1")

    val filterInstanceCreationUser = SampleUser(username = "ficUser", email = "fic@somewhere.com", organizationId = orgs.root.id)

    val all = Seq(admin1, noGroupUser1, noGroupUser2, sampleGroupUser1,
      sampleSubgroupUser1,
      anotherGroupUser1,
      org1User1, org1Admin1, org2User1, org2Admin1,
      emailQweUser, emailQwertyUser, emailAsdUser,
      dataFiltersUser,
      pwdResetUser,
      pwdUpdateUser,
      filterInstanceCreationUser,
      userForOrgSearch
    )
  }

  object apps {
    val defaultApp = ApplicationInfoSample(id = "acceptance", name = "testName", clientSecret = "test")

    val all = Seq(defaultApp)
  }
}

case class SampleOrg(name: String,
                     id: String = SampleData.generateId,
                     parentOrganizationId: Option[String] = None,
                     status: String = "ACTIVE",
                     applicationIds: Set[String] = Set(),
                     created: ZonedDateTime = ZonedDateTime.now(),
                     updated: ZonedDateTime = ZonedDateTime.now(),
                     dataFilterInstances: Set[DataFilterInstance] = Set(),
                     signUpEnabled: Boolean = false,
                     signUpGroupIds: Set[String] = Set()
                    )

case class SampleGroup(name: String,
                       id: String = SampleData.generateId,
                       organizationId: String,
                       parentGroupId: Option[String] = None,
                       grantsPermissions: Set[Permission] = Set(),
                       forChildOrgs: Boolean = false,
                       created: ZonedDateTime = ZonedDateTime.now(),
                       updated: ZonedDateTime = ZonedDateTime.now(),
                       dataFilterInstances: Set[DataFilterInstance] = Set()
                      )

case class SampleUser(username: String,
                      organizationId: String,
                      id: String = SampleData.generateId,
                      firstName: String = "John",
                      lastName: String = "Doe",
                      email: String = "john.doe@company.com",
                      password: String = "",
                      passwordPlain: String = SampleData.DEFAULT_PASSWORD,
                      status: String = "ACTIVE",
                      groupIds: Set[String] = Set(),
                      created: ZonedDateTime = ZonedDateTime.now(),
                      updated: ZonedDateTime = ZonedDateTime.now(),
                      dataFilterInstances: Set[DataFilterInstance] = Set()
                     ) {
  def encodePwd: SampleUser = {
    val salt = "sampleSalt".getBytes
    val hash = computeHash(passwordPlain, salt, 1, 24)

    val pwd = s"1:${toBase64(salt)}:${toBase64(hash)}"
    this.copy(password = pwd)
  }

  private def computeHash(password: String, salt: Array[Byte], iterations: Int, outputBytes: Int): Array[Byte] = {
    // Following is implementation from JDK
    val spec: PBEKeySpec = new PBEKeySpec(password.toCharArray, salt, iterations, outputBytes * 8)
    val skf: SecretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
    skf.generateSecret(spec).getEncoded
  }

  private def toBase64(bytes: Array[Byte]): String = {
    (new sun.misc.BASE64Encoder).encode(bytes)
  }
}

case class ApplicationInfoSample(
                                  id: String,
                                  name: String,
                                  clientSecret: String,
                                  desc: Option[String] = None,
                                  url: Option[String] = None,
                                  created: ZonedDateTime = ZonedDateTime.now(),
                                  updated: ZonedDateTime = ZonedDateTime.now()
                                )


case class DataFilterInfoSample(
                                 id: String = SampleData.generateId,
                                 fieldName: String,
                                 fieldDesc: String,
                                 valuesQuerySettings: Option[DataFilterInfoSettingsSample] = None,
                                 displayName: Option[String] = None,
                                 showValueOnly: Boolean = false
                               )

case class DataFilterInfoSettingsSample(validValuesQuery: String = "SamplevalidValuesQuery",
                                        dbName: String = "SampleDbName",
                                        dbType: DBType = DBType.MONGO,
                                        dataType: String = "SampleDataType",
                                        collectionName: Option[String] = None)

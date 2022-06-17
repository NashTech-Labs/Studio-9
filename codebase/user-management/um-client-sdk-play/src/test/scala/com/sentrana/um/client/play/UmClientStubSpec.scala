package com.sentrana.um.client.play

import com.sentrana.um.client.play.exceptions.UmAuthenticationException
import com.sentrana.umserver.shared.dtos.DataFilterInstance
import com.sentrana.umserver.shared.dtos.enums.FilterOperator
import org.scalatest.{ MustMatchers, WordSpec, OptionValues }
import play.api.test.Helpers._

/**
 * Created by Paul Lysak on 21.06.16.
 */
class UmClientStubSpec extends WordSpec with MustMatchers with OptionValues {
  "UmClientStub" must {
    "issue and validate token to freshly added user" in {
      val stub = new UmClientStub()
      val u = stub.addUser("u1")
      val t = stub.issueToken(u)
      val actualUser = await(stub.validateAccessToken(t)).value
      actualUser.username must be("u1")
    }

    "find freshly added user" in {
      val stub = new UmClientStub()
      val u1 = stub.addUser("u1")
      val u2 = stub.addUser("u2")
      val v1 = stub.addUser("v1")
      val v2 = stub.addUser("v2")

      await(stub.findUsers()).map(_.username) must contain only ("u1", "u2", "v1", "v2")
      await(stub.findUsers(username = Option("u2"))).map(_.username) must contain only ("u2")
      await(stub.findUsers(email = Option(u1.email))).map(_.username) must contain only ("u1")
      await(stub.findUsers(emailPrefix = Option("v"))).map(_.username) must contain only ("v1", "v2")
    }

    "invalidate access token" in {
      val stub = new UmClientStub()
      val u = stub.addUser("u1")
      val t = stub.issueToken(u)
      val u1 = await(stub.validateAccessToken(t)).value
      u1.username must be("u1")

      await(stub.signOut(t))

      await(stub.validateAccessToken(t)) must be(empty)
    }

    "sign up a user" in {
      val stub = new UmClientStub()
      val uid = await(stub.signUp(
        orgId = stub.rootOrgId,
        username = "u1",
        email = "q@w.e",
        password = "pass",
        firstName = "fname",
        lastName = "lname"
      ))
      val actualUser = await(stub.getUserMandatory(uid))
      actualUser.id must be(uid)
      actualUser.username must be("u1")
      actualUser.email must be("q@w.e")
    }

    "find filters" in {
      val stub = new UmClientStub()
      stub.addFilter("field1")
      stub.addFilter("field2")

      await(stub.findFilters()).map(_.fieldName) must contain only ("field1", "field2")
      await(stub.findFilters(fieldName = Option("field1"))).map(_.fieldName) must contain only ("field1")
    }

    "define and get filter instances" in {
      val stub = new UmClientStub()
      val f1 = stub.addFilter("field1")
      val f2 = stub.addFilter("field2")
      val u = stub.addUser("u1")

      u.dataFilterInstances must be(empty)
      await(stub.getFilterInstances(u.id)) must be(empty)

      val fInst = DataFilterInstance(f1.id, FilterOperator.IN, Set("zxcv"))
      val updU = await(stub.setFilterInstance(u, fInst, "tokenDoesNotMatter"))
      updU.dataFilterInstances must contain only (fInst)

      await(stub.getUserMandatory(u.id)).dataFilterInstances must contain only (fInst)
      await(stub.getFilterInstances(u.id)) must be(Map("field1" -> fInst))
    }

    "reset password" in {
      val stub = new UmClientStub()
      val u = stub.addUser("u1", password = "pwd1")

      await(stub.initPasswordReset(u.email))

      intercept[UmAuthenticationException] {
        await(stub.completePasswordReset(u.email, "wrongCode", "pwd2"))
      }
      stub.getPassword(u.id).value must be("pwd1")

      val code = stub.getPasswordResetCode(u.email).value
      await(stub.completePasswordReset(u.email, code, "pwd2"))
      stub.getPassword(u.id).value must be("pwd2")
    }

    "update password" in {
      val stub = new UmClientStub()
      val u = stub.addUser("u1", password = "pwd1")
      val t = stub.issueToken(u)

      intercept[UmAuthenticationException] {
        await(stub.updatePassword(t, "wrongPwd", "pwd2"))
      }
      stub.getPassword(u.id).value must be("pwd1")

      await(stub.updatePassword(t, "pwd1", "pwd2"))
      stub.getPassword(u.id).value must be("pwd2")
    }
  }
}

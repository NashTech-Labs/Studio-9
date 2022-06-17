package com.sentrana.umserver

import com.sentrana.umserver.utils.PasswordHash
import org.scalatest.{ MustMatchers, WordSpec }

/**
 * Created by Paul Lysak on 09.05.16.
 */
class PasswordHashSpec extends WordSpec with MustMatchers {
  private val rootPwdHash = "2710:cm9vdFNhbHQ=:+y1NlIknpwZJO787hByDw2bMlXnXurpd"

  "PasswordHash" must {
    "generate hash" in {
      val salt = "rootSalt".getBytes
      val hash = PasswordHash.computeHash("root", salt, 10000, 24)
      val actualHashStr = (new PasswordHash(salt, hash, 10000)).toBase64String
      actualHashStr must be (rootPwdHash)
    }

    "verify password" in {
      PasswordHash.parse(rootPwdHash).checkPassword("root") must be (true)
    }
  }
}

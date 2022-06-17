package com.sentrana.umserver

import java.net.ServerSocket

/**
  * Created by Paul Lysak on 09.09.16.
  */
object TestUtils {
  def findFreePorts(n: Int): Seq[Int] = {
    (for (i <- 0 until n)
      yield {
        val s = new ServerSocket(0)
        s.setReuseAddress(true)
        (s.getLocalPort, s)
      }).
      map({ case (p, s) => s.close; p; })
  }
}

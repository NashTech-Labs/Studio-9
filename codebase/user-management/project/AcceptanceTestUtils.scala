import java.net.ServerSocket

import de.flapdoodle.embed.mongo.distribution.Version
import sbt.Keys._
import sbt._
import com.github.simplyscala._

/**
  * Utilities for acceptance tests infrastructure.
  * The idea is to run specific version of um-server under umServiceTestLauncher with embedded MongoDb,
  * and run integration tests from umClientSdkPlayAcceptance over it. Those testz include separate Play! application
  * which needs to be isolated in separate JVM, therefore JVM is forked for integration tests.
  */
object AcceptanceTestUtils {
  private lazy val ports = findPorts(3)
  lazy val mongoPort = ports.head
  lazy val smtpPort = ports.tail.head
  private var customServerPort: Option[Int] = None
  lazy val serverPort = customServerPort.getOrElse(ports.tail.tail.head)

  lazy val mongoUri = s"mongodb://localhost:$mongoPort/test"

  lazy val launchWithEmbeddedMongoCommand: Command = Command.args("launch", "[serverPort]")({(state, args) =>
    args.headOption.foreach({portStr =>
      customServerPort = Option(portStr.toInt)
    })
    state.log.info(s"Starting embedded MongoDB on port $mongoPort...")
    mongoCtrl.start()
    state.log.info(s"Starting test HTTP server on port $serverPort...")
    Command.process(s"run $serverPort -Dconfig.resource=integration.conf -Dmongodb.um.uri=$mongoUri -Dplay.mailer.host=localhost -Dplay.mailer.port=$smtpPort -Dplay.mailer.mock=false", state)
  })

  lazy val acceptanceCommand: Command = Command.command("acceptance")({(state) =>
      var s = state
      s = Command.process("project umServiceTestLauncher", s)
      s = Command.process("set PlayKeys.playInteractionMode := play.sbt.StaticPlayNonBlockingInteractionMode", s)
      s = Command.process("launch", s)
      //TODO abort further steps if launch fails
      state.log.debug(s"Starting tests with mongoPort=$mongoPort, serverPort=$serverPort...")
      s = Command.process("project umClientSdkPlayAcceptance", s)
      val serverUrl = s"http://localhost:$serverPort"
      s = Command.process(s"""set javaOptions in IntegrationTest ++= Seq("-Dmongodb.um.uri=$mongoUri", "-Dplay.mailer.port=$smtpPort", "-Dsentrana.um.server.url=$serverUrl") """.trim, s)
      s = Command.process(s"it:test", s)
      s
  })

  private val mongoCtrl = new MongoEmbedDatabase {
    private var mongodProps: MongodProps = _

    def start(): Unit = {
      mongodProps = mongoStart(port = mongoPort, version = Version.V3_2_0)
    }

    //finalize is good enough to clean up embedded mongo instance in most cases
    override def finalize(): Unit = {
      super.finalize()
      if(mongodProps != null)
        mongoStop(mongodProps)
    }
  }

  private def findPorts(n: Int): Seq[Int] = {
    (for (i <- 0 until n)
      yield {
        val s = new ServerSocket(0)
        s.setReuseAddress(true)
        (s.getLocalPort, s)
      }).
      map({ case (p, s) => s.close; p; })
  }
}

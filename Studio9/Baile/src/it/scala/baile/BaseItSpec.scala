package baile

import com.whisk.docker.impl.spotify.DockerKitSpotify
import com.whisk.docker.scalatest.DockerTestKit
import org.scalatest.BeforeAndAfter
import org.scalatest.time.{ Minutes, Span }

trait BaseItSpec extends BaseSpec with DockerTestKit with DockerKitSpotify with BeforeAndAfter {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(2, Minutes)))

}

package cortex.testkit

import cortex.task.StorageAccessParams.LocalAccessParams
import cortex.task.transform.splitter.SplitterParams.SplitterTaskParams
import org.scalacheck.rng.Seed
import org.scalacheck.{ Arbitrary, Gen }

trait WithArbitrary {
  def splitterParamsArbitrary(): SplitterTaskParams = {
    (for {
      inputPath <- Arbitrary.arbitrary[String]
    } yield {
      SplitterTaskParams(Seq(inputPath), LocalAccessParams)
    }).pureApply(Gen.Parameters.default, Seed.random())
  }
}

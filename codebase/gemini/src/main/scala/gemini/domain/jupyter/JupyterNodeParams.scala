package gemini.domain.jupyter

case class JupyterNodeParams(
  numberOfCpus: Option[Double],
  numberOfGpus: Option[Double]
)

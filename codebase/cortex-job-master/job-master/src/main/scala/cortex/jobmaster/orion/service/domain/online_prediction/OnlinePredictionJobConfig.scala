package cortex.jobmaster.orion.service.domain.online_prediction

/**
 *
 * @param maxPredictionsPerResultFile maximal number of rows per csv with online prediction results
 */
case class OnlinePredictionJobConfig(
    maxPredictionsPerResultFile: Int
)

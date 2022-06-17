from deepcortex.pipelines import PublishedOperator, PipelineOperator
from typing import Tuple


@PublishedOperator('get random number')
class GetRandomNumber(PipelineOperator):

    def apply(self) -> Tuple[int]:
        return 4,  # chosen by fair dice roll, guaranteed to be random

    def configure(self):
        pass

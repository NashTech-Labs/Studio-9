from typing import Tuple

from deepcortex.pipelines import PublishedOperator, PipelineOperator, OperatorInput
from deepcortex.pipelines.published_operator import OperatorOutput
from deepcortex.pipelines.asset_type import AssetType

class Foo:
    pass


class Bar:
    pass


class Baz(Foo):
    pass


class Qux(Baz, Bar):
    pass


class MyModel:
    pass


@PublishedOperator(
    name='test pipeline operator',
    description='test pipeline operator description',
    parameters={
        'album': {
            'asset_type': AssetType.Album
        }
    },
    inputs={
        'qux': OperatorInput(description='a qux'),
        'baz': OperatorInput(covariate=False),
    },
    outputs=(OperatorOutput('loaded model'), )
)
class TestPipelineOperator(PipelineOperator):

    def apply(self, x: int, baz: Baz, qux: Qux = None) -> Tuple[MyModel]:
        pass

    def configure(self, album: str):
        pass
